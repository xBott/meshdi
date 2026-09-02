package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import lombok.Setter;
import me.bottdev.meshdi.api.Context;
import java.nio.file.Path;
import java.util.List;

import me.bottdev.meshdi.api.exceptions.ContextBootstrapException;
import me.bottdev.meshdi.moduleit.api.LeakDetectorResult;
import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleClassLoader;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.kern.commons.diagnostic.DiagnosticsBuilder;
import me.bottdev.meshdi.moduleit.api.ModuleState;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStartDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStopDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleUnloadDiagnostic;

import me.bottdev.meshdi.api.MeshRegistration;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterExecuteException;
import me.bottdev.meshdi.core.SimpleContextBootstrap;
import me.bottdev.meshdi.core.mesh.MeshContextSelectionStrategies;
import me.bottdev.kern.dependency.exceptions.ResolverForgetException;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import org.semver4j.Semver;

import java.net.MalformedURLException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

class SimpleModuleHandle implements InternalModuleHandle {

    public static SimpleModuleHandle ofResolved(
            @NonNull ModuleCandidate candidate
    ) {
        return new SimpleModuleHandle(candidate);
    }

    private final ModuleCandidate candidate;
    @Setter private ModuleState state = ModuleState.RESOLVED;
    @Setter private ClassLoader classLoader = null;
    @Setter private Context context = null;
    @Setter private List<Path> libraries = List.of();

    private SimpleModuleHandle(
            @NonNull ModuleCandidate candidate
    ) {
        this.candidate = candidate;
    }

    @Override
    public ModuleCandidate candidate() {
        return candidate;
    }

    @Override
    public ModuleState state() {
        return state;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return candidate.descriptor();
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public ClassLoader classLoader() {
        return classLoader;
    }

    @Override
    public List<Path> libraries() {
        return libraries;
    }

    @Override
    public void completePreparation(List<Path> libraries) {
        this.libraries = libraries;
        this.state = ModuleState.READY;
    }

    @Override
    public boolean doLoad(SimpleModuleManager manager, DiagnosticsBuilder<ModuleLoadDiagnostic> builder) {
        String moduleId = descriptor().id();
        Semver version = descriptor().semver();

        if (state != ModuleState.READY) {
            builder.append(new ModuleLoadDiagnostic.IncorrectState(moduleId, state));
            return false;
        }

        try {
            ModuleClassLoader classLoader = manager.createClassLoader(this);
            this.classLoader = classLoader;
            
            Set<String> exports = descriptor().exports();
            if (exports != null && !exports.isEmpty()) {
                manager.environment().exportRegistry().register(moduleId, exports, classLoader);
                builder.append(new ModuleLoadDiagnostic.ExportsRegistered(moduleId, exports.size()));
            }

            this.state = ModuleState.LOADED;
            builder.append(new ModuleLoadDiagnostic.Loaded(moduleId, version));
            return true;

        } catch (MalformedURLException ex) {
            builder.append(new ModuleLoadDiagnostic.MalformedLibraryUrl(moduleId, Path.of(ex.getMessage()), ex));
            return false;
        }
    }

    @Override
    public boolean doStart(SimpleModuleManager manager, DiagnosticsBuilder<ModuleStartDiagnostic> builder) {
        String moduleId = descriptor().id();
        Semver version = descriptor().semver();

        if (state != ModuleState.LOADED && state != ModuleState.STOPPED) {
            builder.append(new ModuleStartDiagnostic.IncorrectState(moduleId, state));
            return false;
        }

        this.state = ModuleState.STARTING;

        boolean success = false;
        try {
            Context moduleContext = SimpleContextBootstrap.bootstrap(this.classLoader)
                    .id(moduleId)
                    .build();

            List<String> sees = descriptor().getVersionedDependencies().stream()
                    .map(VersionedDependencyRequest::key)
                    .toList();

            MeshRegistration registration = new MeshRegistration(moduleContext, sees);
            Context meshViewContext = manager.contextMesh().register(registration);

            meshViewContext.start();

            this.context = meshViewContext;
            this.state = ModuleState.STARTED;
            success = true;

        } catch (ContextBootstrapException ex) {
            builder.append(new ModuleStartDiagnostic.BootstrapFailed(moduleId, ex));
        } catch (ContextBuildException ex) {
            builder.append(new ModuleStartDiagnostic.BuildFailed(moduleId, ex));
        } catch (ContextStartException ex) {
            builder.append(new ModuleStartDiagnostic.ContextNotStarted(moduleId, ex));
        } catch (MeshRegisterException ex) {
            builder.append(new ModuleStartDiagnostic.MeshRegistrationFailed(moduleId, ex));
        }

        if (!success) {
            this.context = null;
            this.state = ModuleState.START_FAILED;
            return false;
        }

        builder.append(new ModuleStartDiagnostic.Started(moduleId, version));
        return true;
    }

    @Override
    public boolean doStop(SimpleModuleManager manager, DiagnosticsBuilder<ModuleStopDiagnostic> builder) {
        String moduleId = descriptor().id();
        Semver version = descriptor().semver();

        if (state != ModuleState.STARTED) {
            builder.append(new ModuleStopDiagnostic.IncorrectState(moduleId));
            return false;
        }

        this.state = ModuleState.STOPPING;
        String contextId = this.context.id();
        boolean success = false;

        try {
            manager.contextMesh().planUnregister(contextId, MeshContextSelectionStrategies.CASCADE).execute();
            this.context.dispose();

            this.state = ModuleState.STOPPED;
            this.context = null;
            success = true;

        } catch (MeshUnregisterExecuteException ex) {
            builder.append(new ModuleStopDiagnostic.MeshUnregisterExecutionFailed(moduleId, ex));
        } catch (MeshContextSelectionException ex) {
            builder.append(new ModuleStopDiagnostic.MeshUnregisterPlanFailed(moduleId, ex));
        }

        if (!success) {
            this.context = null;
            this.state = ModuleState.STOP_FAILED;
            return false;
        }

        builder.append(new ModuleStopDiagnostic.Stopped(moduleId, version));
        return true;
    }

    @Override
    public CompletableFuture<LeakDetectorResult> doUnload(SimpleModuleManager manager, DiagnosticsBuilder<ModuleUnloadDiagnostic> builder) {
        String moduleId = descriptor().id();
        Semver version = descriptor().semver();

        if (state != ModuleState.STOPPED && state != ModuleState.LOADED) {
            builder.append(new ModuleUnloadDiagnostic.IncorrectState(moduleId, state));
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
            builder.append(new ModuleUnloadDiagnostic.ForgetFailed(moduleId, ex.getDependents()));
        }

        if (!success) {
            this.context = null;
            this.state = ModuleState.UNLOAD_FAILED;
            return CompletableFuture.completedFuture(null);
        }

        ClassLoader unloadedClassLoader = this.classLoader;
        this.classLoader = null;

        manager.leakDetector().track(moduleId, unloadedClassLoader);
        builder.append(new ModuleUnloadDiagnostic.Unloaded(moduleId, version));

        return manager.leakDetector().awaitUnloadAsync(moduleId, Duration.of(10, ChronoUnit.SECONDS));
    }

}
