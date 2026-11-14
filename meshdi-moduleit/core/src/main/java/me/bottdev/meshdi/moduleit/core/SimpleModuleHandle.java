package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import lombok.Setter;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.kern.dependency.exceptions.ResolverForgetException;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.MeshRegistration;
import me.bottdev.meshdi.api.exceptions.ContextBootstrapException;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterExecuteException;
import me.bottdev.meshdi.core.SimpleContextBootstrap;
import me.bottdev.meshdi.core.mesh.MeshContextSelectionStrategies;
import me.bottdev.meshdi.moduleit.api.LeakDetectorResult;
import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleClassLoader;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleState;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStartDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStopDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleUnloadDiagnostic;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleExportConflictException;
import me.bottdev.meshdi.moduleit.core.ModuleHandleSupport.ContextClassLoaderScope;
import org.semver4j.Semver;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/// Handle of a module loaded from a jar with its own class loader.
///
/// ## States
/// Failed states are recoverable: a failed start is rolled back completely, so the module can be started
/// again or unloaded; a failed stop keeps the context so the stop can be retried; a failed unload can be
/// retried once its dependents are gone.
///
/// ## Thread safety
/// Transitions are synchronized on the handle and state is published through volatile fields,
/// so any thread can read a handle while the manager drives it.
class SimpleModuleHandle implements InternalModuleHandle {

    private static final Set<ModuleState> STARTABLE = EnumSet.of(
            ModuleState.LOADED, ModuleState.STOPPED, ModuleState.START_FAILED, ModuleState.UNLOAD_FAILED
    );
    private static final Set<ModuleState> STOPPABLE = EnumSet.of(
            ModuleState.STARTED, ModuleState.STOP_FAILED
    );
    private static final Set<ModuleState> UNLOADABLE = EnumSet.of(
            ModuleState.STOPPED, ModuleState.LOADED, ModuleState.START_FAILED, ModuleState.UNLOAD_FAILED
    );

    public static SimpleModuleHandle ofResolved(
            @NonNull ModuleCandidate candidate) {
        return new SimpleModuleHandle(candidate);
    }

    private final ModuleCandidate candidate;

    @Setter
    private volatile ModuleState state = ModuleState.RESOLVED;
    @Setter
    private volatile ClassLoader classLoader = null;
    @Setter
    private volatile Context context = null;
    @Setter
    private volatile List<Path> libraries = List.of();

    private SimpleModuleHandle(
            @NonNull ModuleCandidate candidate) {
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
    public synchronized void completePreparation(List<Path> libraries) {
        this.libraries = List.copyOf(libraries);
        this.state = ModuleState.READY;
    }

    @Override
    public synchronized boolean doLoad(SimpleModuleManager manager, DiagnosticSink<ModuleLoadDiagnostic> sink) {

        String moduleId = descriptor().id();
        Semver version = descriptor().semver();

        if (state != ModuleState.READY) {
            sink.accept(new ModuleLoadDiagnostic.IncorrectState(moduleId, state));
            return false;
        }

        ModuleClassLoader classLoader;
        try {
            classLoader = manager.createClassLoader(this);

        } catch (MalformedURLException ex) {
            sink.accept(new ModuleLoadDiagnostic.MalformedLibraryUrl(moduleId, Path.of(ex.getMessage()), ex));
            return false;
        }

        Set<String> exports = descriptor().exports();
        if (exports != null && !exports.isEmpty()) {
            try {
                manager.environment().exportRegistry().register(moduleId, exports, classLoader);

            } catch (ModuleExportConflictException ex) {
                closeQuietly(classLoader);
                sink.accept(new ModuleLoadDiagnostic.ExportConflict(moduleId, ex));
                return false;
            }
            sink.accept(new ModuleLoadDiagnostic.ExportsRegistered(moduleId, exports.size()));
        }

        this.classLoader = classLoader;
        this.state = ModuleState.LOADED;

        sink.accept(new ModuleLoadDiagnostic.Loaded(moduleId, version));
        return true;
    }

    @Override
    public synchronized boolean doStart(SimpleModuleManager manager, DiagnosticSink<ModuleStartDiagnostic> sink) {

        String moduleId = descriptor().id();
        Semver version = descriptor().semver();
        ClassLoader moduleLoader = this.classLoader;

        if (!STARTABLE.contains(state) || moduleLoader == null) {
            sink.accept(new ModuleStartDiagnostic.IncorrectState(moduleId, state));
            return false;
        }

        this.state = ModuleState.STARTING;

        // What has to be undone if a later step fails.
        Context builtContext = null;
        Context registeredContext = null;

        try (ContextClassLoaderScope _ = ModuleHandleSupport.contextClassLoader(moduleLoader)) {

            builtContext = SimpleContextBootstrap.bootstrap(moduleLoader)
                    .id(moduleId)
                    .build();

            List<String> sees = ModuleHandleSupport.visibleContextIds(descriptor(), manager.contextMesh());
            registeredContext = manager.contextMesh().register(new MeshRegistration(builtContext, sees));

            registeredContext.start();

            this.context = registeredContext;
            this.state = ModuleState.STARTED;

            sink.accept(new ModuleStartDiagnostic.Started(moduleId, version));
            return true;

        } catch (ContextBootstrapException ex) {
            sink.accept(new ModuleStartDiagnostic.BootstrapFailed(moduleId, ex));

        } catch (ContextBuildException ex) {
            sink.accept(new ModuleStartDiagnostic.BuildFailed(moduleId, ex));

        } catch (ContextStartException ex) {
            sink.accept(new ModuleStartDiagnostic.ContextNotStarted(moduleId, ex));

        } catch (MeshRegisterException ex) {
            sink.accept(new ModuleStartDiagnostic.MeshRegistrationFailed(moduleId, ex));

        } catch (RuntimeException | LinkageError ex) {
            // Module code runs here (definitions, eager beans), so anything can come out of it.
            if (builtContext == null) {
                sink.accept(new ModuleStartDiagnostic.BootstrapFailed(moduleId, ex));
            } else {
                sink.accept(new ModuleStartDiagnostic.ContextNotStarted(moduleId, ex));
            }
        }

        rollbackStart(manager, moduleId, moduleLoader, builtContext, registeredContext, sink);

        this.context = null;
        this.state = ModuleState.START_FAILED;
        return false;
    }

    /// Undoes a partially completed start: removes the context from the mesh and disposes it together with
    /// every bean that was already created, so the module can be started again or unloaded.
    private void rollbackStart(
            SimpleModuleManager manager,
            String moduleId,
            ClassLoader moduleLoader,
            Context builtContext,
            Context registeredContext,
            DiagnosticSink<ModuleStartDiagnostic> sink
    ) {
        if (registeredContext != null) {
            try {
                manager.contextMesh()
                        .planUnregister(registeredContext.id(), MeshContextSelectionStrategies.CASCADE)
                        .execute();

            } catch (MeshContextSelectionException | MeshUnregisterExecuteException | RuntimeException ex) {
                sink.accept(new ModuleStartDiagnostic.RollbackFailed(moduleId, ex));
            }
        }

        Context toDispose = registeredContext != null ? registeredContext : builtContext;
        if (toDispose != null) {
            try (ContextClassLoaderScope _ = ModuleHandleSupport.contextClassLoader(moduleLoader)) {
                toDispose.dispose();

            } catch (RuntimeException ex) {
                sink.accept(new ModuleStartDiagnostic.RollbackFailed(moduleId, ex));
            }
        }
    }

    @Override
    public synchronized boolean doStop(SimpleModuleManager manager, DiagnosticSink<ModuleStopDiagnostic> sink) {

        String moduleId = descriptor().id();
        Semver version = descriptor().semver();

        if (!STOPPABLE.contains(state)) {
            sink.accept(new ModuleStopDiagnostic.IncorrectState(moduleId));
            return false;
        }

        boolean retry = state == ModuleState.STOP_FAILED;
        Context stopping = this.context;

        this.state = ModuleState.STOPPING;

        if (stopping != null) {
            String contextId = stopping.id();

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

            try (ContextClassLoaderScope _ = ModuleHandleSupport.contextClassLoader(this.classLoader)) {
                stopping.dispose();

            } catch (RuntimeException ex) {
                sink.accept(new ModuleStopDiagnostic.ContextDisposeFailed(moduleId, ex));
                this.state = ModuleState.STOP_FAILED;
                return false;
            }
        }

        this.context = null;
        this.state = ModuleState.STOPPED;

        sink.accept(new ModuleStopDiagnostic.Stopped(moduleId, version));
        return true;
    }

    @Override
    public synchronized CompletableFuture<LeakDetectorResult> doUnload(SimpleModuleManager manager, DiagnosticSink<ModuleUnloadDiagnostic> sink) {

        String moduleId = descriptor().id();
        Semver version = descriptor().semver();

        if (!UNLOADABLE.contains(state)) {
            sink.accept(new ModuleUnloadDiagnostic.IncorrectState(moduleId, state));
            return CompletableFuture.completedFuture(null);
        }

        this.state = ModuleState.UNLOADING;

        try {
            manager.dependencyResolver().state().forget(moduleId);

        } catch (ResolverForgetException ex) {
            // Nothing was changed: the class loader stays open, so the module can still be started,
            // and the unload can be retried once the dependents are unloaded.
            sink.accept(new ModuleUnloadDiagnostic.ForgetFailed(moduleId, ex.getDependents()));
            this.state = ModuleState.UNLOAD_FAILED;
            return CompletableFuture.completedFuture(null);
        }

        manager.environment().exportRegistry().unregister(moduleId);
        manager.removeHandle(moduleId);

        ClassLoader unloadedClassLoader = this.classLoader;
        this.classLoader = null;
        this.context = null;
        this.libraries = List.of();

        // Release jar file handles now instead of waiting for GC, so the jar can be replaced right away.
        closeQuietly(unloadedClassLoader);

        sink.accept(new ModuleUnloadDiagnostic.Unloaded(moduleId, version));

        if (unloadedClassLoader == null) {
            return CompletableFuture.completedFuture(new LeakDetectorResult.Disabled());
        }
        manager.leakDetector().track(moduleId, unloadedClassLoader);
        return manager.leakDetector().awaitUnloadAsync(moduleId, manager.leakCheckTimeout());
    }

    private static void closeQuietly(ClassLoader classLoader) {
        if (classLoader instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // The loader is unreachable after unload either way.
            }
        }
    }

}
