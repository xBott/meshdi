package me.bottdev.meshdi.moduleit.core;

import java.util.EnumSet;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleExportConflictException;
import lombok.Builder;
import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.kern.dependency.exceptions.ResolverForgetException;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.MeshRegistration;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterExecuteException;
import me.bottdev.meshdi.core.mesh.MeshContextSelectionStrategies;
import me.bottdev.meshdi.moduleit.api.LeakDetectorResult;
import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleState;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStartDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStopDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleUnloadDiagnostic;
import org.semver4j.Semver;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class VirtualModuleHandle implements InternalModuleHandle {

    private final ModuleDescriptor descriptor;
    private final Context context;
    private final boolean persistent;
    private final boolean disposeOnStop;
    private final boolean trackClassLoaderOnUnload;
    private final List<Path> libraries;
    private final VirtualModuleCandidate candidate;

    private static final Set<ModuleState> STARTABLE = EnumSet.of(
            ModuleState.LOADED, ModuleState.STOPPED, ModuleState.START_FAILED, ModuleState.UNLOAD_FAILED
    );
    private static final Set<ModuleState> STOPPABLE = EnumSet.of(
            ModuleState.STARTED, ModuleState.STOP_FAILED
    );
    private static final Set<ModuleState> UNLOADABLE = EnumSet.of(
            ModuleState.STOPPED, ModuleState.LOADED, ModuleState.START_FAILED, ModuleState.UNLOAD_FAILED
    );

    private volatile ModuleState state = ModuleState.RESOLVED;

    public VirtualModuleHandle(ModuleDescriptor descriptor, Context context) {
        this(descriptor, context, false, false, false, List.of());
    }

    @Builder
    public VirtualModuleHandle(
            @NonNull ModuleDescriptor descriptor, 
            @NonNull Context context, 
            boolean persistent,
            boolean disposeOnStop, 
            boolean trackClassLoaderOnUnload,
            List<Path> libraries
    ) {
        this.descriptor = descriptor;
        this.context = context;
        this.persistent = persistent;
        this.disposeOnStop = disposeOnStop;
        this.trackClassLoaderOnUnload = trackClassLoaderOnUnload;
        this.libraries = libraries != null ? libraries : List.of();
        this.candidate = new VirtualModuleCandidate(this);
    }

    @Override
    public ModuleCandidate candidate() {
        return candidate;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public ModuleState state() {
        return state;
    }

    @Override
    public boolean isPersistent() {
        return persistent;
    }

    @Override
    public ClassLoader classLoader() {
        return context != null ? context.getClass().getClassLoader() : ClassLoader.getSystemClassLoader();
    }

    @Override
    public List<Path> libraries() {
        return libraries;
    }

    @Override
    public synchronized void completePreparation(List<Path> libraries) {
        this.state = ModuleState.READY;
    }

    @Override
    public synchronized boolean doLoad(SimpleModuleManager manager, DiagnosticSink<ModuleLoadDiagnostic> sink) {
        String moduleId = descriptor.id();
        Semver version = descriptor.semver();

        if (state != ModuleState.READY) {
            sink.accept(new ModuleLoadDiagnostic.IncorrectState(moduleId, state));
            return false;
        }

        Set<String> exports = descriptor.exports();
        if (exports != null && !exports.isEmpty()) {
            try {
                manager.environment().exportRegistry().register(moduleId, exports, classLoader());

            } catch (ModuleExportConflictException ex) {
                sink.accept(new ModuleLoadDiagnostic.ExportConflict(moduleId, ex));
                return false;
            }
            sink.accept(new ModuleLoadDiagnostic.ExportsRegistered(moduleId, exports.size()));
        }

        this.state = ModuleState.LOADED;
        sink.accept(new ModuleLoadDiagnostic.Loaded(moduleId, version));
        return true;
    }

    @Override
    public synchronized boolean doStart(SimpleModuleManager manager, DiagnosticSink<ModuleStartDiagnostic> sink) {
        String moduleId = descriptor.id();
        Semver version = descriptor.semver();

        if (!STARTABLE.contains(state)) {
            sink.accept(new ModuleStartDiagnostic.IncorrectState(moduleId, state));
            return false;
        }

        this.state = ModuleState.STARTING;
        boolean success = false;

        try {
            List<String> sees = ModuleHandleSupport.visibleContextIds(descriptor, manager.contextMesh());

            MeshRegistration registration = new MeshRegistration(context, sees);
            manager.contextMesh().register(registration);
            
            this.state = ModuleState.STARTED;
            success = true;

        } catch (MeshRegisterException ex) {
            sink.accept(new ModuleStartDiagnostic.MeshRegistrationFailed(moduleId, ex));
        }

        if (!success) {
            this.state = ModuleState.START_FAILED;
            return false;
        }

        sink.accept(new ModuleStartDiagnostic.Started(moduleId, version));
        return true;
    }

    @Override
    public synchronized boolean doStop(SimpleModuleManager manager, DiagnosticSink<ModuleStopDiagnostic> sink) {
        String moduleId = descriptor.id();
        Semver version = descriptor.semver();

        if (!STOPPABLE.contains(state)) {
            sink.accept(new ModuleStopDiagnostic.IncorrectState(moduleId));
            return false;
        }

        boolean retry = state == ModuleState.STOP_FAILED;
        this.state = ModuleState.STOPPING;

        String contextId = context.id();

        // A retry may find the context already removed from the mesh by the previous attempt.
        if (!retry || manager.contextMesh().contains(contextId)) {
            try {
                manager.contextMesh().planUnregister(contextId, MeshContextSelectionStrategies.CASCADE).execute();

            } catch (MeshUnregisterExecuteException ex) {
                sink.accept(new ModuleStopDiagnostic.MeshUnregisterExecutionFailed(moduleId, ex));
                this.state = ModuleState.STOP_FAILED;
                return false;

            } catch (MeshContextSelectionException ex) {
                sink.accept(new ModuleStopDiagnostic.MeshUnregisterPlanFailed(moduleId, ex));
                this.state = ModuleState.STOP_FAILED;
                return false;
            }
        }

        if (disposeOnStop) {
            try {
                context.dispose();

            } catch (RuntimeException ex) {
                sink.accept(new ModuleStopDiagnostic.ContextDisposeFailed(moduleId, ex));
                this.state = ModuleState.STOP_FAILED;
                return false;
            }
        }

        this.state = ModuleState.STOPPED;

        sink.accept(new ModuleStopDiagnostic.Stopped(moduleId, version));
        return true;
    }

    @Override
    public synchronized CompletableFuture<LeakDetectorResult> doUnload(SimpleModuleManager manager, DiagnosticSink<ModuleUnloadDiagnostic> sink) {
        String moduleId = descriptor.id();
        Semver version = descriptor.semver();

        if (!UNLOADABLE.contains(state)) {
            sink.accept(new ModuleUnloadDiagnostic.IncorrectState(moduleId, state));
            return CompletableFuture.completedFuture(null);
        }

        this.state = ModuleState.UNLOADING;
        boolean success = false;

        try {
            manager.dependencyResolver().state().forget(moduleId);
            manager.environment().exportRegistry().unregister(moduleId);
            manager.removeHandle(moduleId);
            success = true;

        } catch (ResolverForgetException ex) {
            sink.accept(new ModuleUnloadDiagnostic.ForgetFailed(moduleId, ex.getDependents()));
        }

        if (!success) {
            this.state = ModuleState.UNLOAD_FAILED;
            return CompletableFuture.completedFuture(null);
        }

        sink.accept(new ModuleUnloadDiagnostic.Unloaded(moduleId, version));

        if (trackClassLoaderOnUnload) {
            manager.leakDetector().track(moduleId, classLoader());
            return manager.leakDetector().awaitUnloadAsync(moduleId, manager.leakCheckTimeout());
        } else {
            return CompletableFuture.completedFuture(new LeakDetectorResult.Disabled());
        }
    }
}
