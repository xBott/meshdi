package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.commons.diagnostic.DiagnosticsBuilder;
import me.bottdev.kern.commons.diagnostic.ListDiagnostics;
import me.bottdev.kern.commons.wrapper.DiagnosticResult;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.dependency.DependentContainer;
import me.bottdev.kern.dependency.ResolutionResult;
import me.bottdev.kern.dependency.StatefulDependencyResolver;
import me.bottdev.kern.dependency.containers.SimpleDependentContainer;
import me.bottdev.kern.dependency.exceptions.ResolverForgetException;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.VersionRange;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterExecuteException;
import me.bottdev.meshdi.core.SimpleContextBootstrap;
import me.bottdev.meshdi.core.mesh.DagContextMesh;
import me.bottdev.meshdi.core.mesh.MeshContextSelectionStrategies;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStartDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStopDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleUnloadDiagnostic;
import me.bottdev.meshdi.moduleit.api.exceptions.*;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SimpleModuleManager implements ModuleManager {

    private final StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver;
    private final ModuleLoadEnvironment environment;
    private final ModuleClassLoaderLeakDetector leakDetector;

    private final Map<String, SimpleModuleHandle> handles = new LinkedHashMap<>();
    private final DagContextMesh contextMesh = new DagContextMesh();

    public SimpleModuleManager(
            @NonNull StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver,
            @NonNull ModuleLoadEnvironment environment,
            @NonNull ModuleClassLoaderLeakDetector leakDetector
    ) {
        this.dependencyResolver = dependencyResolver;
        this.environment = environment;
        this.leakDetector = leakDetector;
    }


    @Override
    public ModuleLoadEnvironment environment() {
        return environment;
    }

    @Override
    public ModuleClassLoaderLeakDetector leakDetector() {
        return leakDetector;
    }

    @Override
    public List<ModuleHandle> getHandles() {
        return handles.values().stream()
                .map(handle -> (ModuleHandle)handle)
                .toList();
    }

    @Override
    public boolean exists(String id) {
        return handles.containsKey(id);
    }

    @Override
    public ModuleHandle getHandle(String id) {
        return handles.get(id);
    }

    @Override
    public List<ModuleHandle> getDependencyHandles(String id) {
        if (!exists(id)) return List.of();
        return dependencyResolver.state().dependenciesOf(id).stream()
                .map(this::getHandle)
                .toList();
    }

    @Override
    public List<ModuleHandle> getDependentHandles(String id) {
        if (!exists(id)) return List.of();
        return dependencyResolver.state().dependentsOf(id).stream()
                .map(this::getHandle)
                .toList();
    }

    private Collection<ModuleCandidate> prepareCandidates(
            List<ModuleCandidate> candidates,
            DiagnosticsBuilder<ModuleLoadDiagnostic> diagnosticsBuilder
    ) {

        Map<String, ModuleCandidate> uniqueCandidates = new HashMap<>();

        for (ModuleCandidate candidate : candidates) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            VersionRange requiredApiVersion = descriptor.apiVersion();

            if (exists(moduleId)) {
                diagnosticsBuilder.append(ModuleLoadDiagnostic.alreadyLoaded(moduleId));
                continue;
            }

            if (uniqueCandidates.containsKey(moduleId)) {
                diagnosticsBuilder.append(ModuleLoadDiagnostic.duplicate(moduleId));
                continue;
            }

            if (!requiredApiVersion.satisfies(environment.apiVersion())) {
                diagnosticsBuilder.append(ModuleLoadDiagnostic.apiVersionMismatch(
                        moduleId,
                        requiredApiVersion,
                        environment.apiVersion())
                );
                continue;
            }

            uniqueCandidates.put(moduleId, candidate);

        }

        return uniqueCandidates.values();
    }

    private DiagnosticResult<ResolutionResult<String, ModuleCandidate>, ModuleLoadDiagnostic> resolveCandidates(
            Collection<ModuleCandidate> uniqueCandidates,
            DiagnosticsBuilder<ModuleLoadDiagnostic> diagnosticsBuilder
    ) {

        DependentContainer<String, ModuleCandidate> container = SimpleDependentContainer.<String, ModuleCandidate>builder()
                .add(uniqueCandidates)
                .build();

        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, DependencyDiagnostic> diagnosticResult =
                dependencyResolver.resolveAndRemember(container);

        if (diagnosticResult.isPresent()) {
            return DiagnosticResult.success(diagnosticResult.unwrap(), diagnosticsBuilder.build());

        } else {
            diagnosticsBuilder.append(ModuleLoadDiagnostic.badResolution(diagnosticResult.unwrapDiagnostics()));
            return DiagnosticResult.failure(diagnosticsBuilder.build());

        }

    }

    @Override
    public Diagnostics<ModuleLoadDiagnostic> load(ModuleRepository repository) throws CandidateListException {

        List<ModuleCandidate> candidates = repository.candidates();

        DiagnosticsBuilder<ModuleLoadDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        Collection<ModuleCandidate> uniqueCandidates = prepareCandidates(candidates, diagnosticsBuilder);
        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, ModuleLoadDiagnostic> diagnosticResult =
                resolveCandidates(uniqueCandidates, diagnosticsBuilder);

        if (!diagnosticResult.isPresent()) {
            return diagnosticResult.unwrapDiagnostics();
        }

        ResolutionResult<String, ModuleCandidate> resolutionResult = diagnosticResult.unwrap();

        for (ModuleCandidate candidate : resolutionResult.ordered()) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            SemVersion version = descriptor.version();
            List<String> dependencyIds = descriptor.getVersionedDependencies().stream()
                    .map(VersionedDependencyRequest::key)
                    .toList();
            Set<String> exports = descriptor.exports();

            ClassLoader classLoader = candidate.openClassLoader(environment, dependencyIds);
            SimpleModuleHandle handle = new SimpleModuleHandle(candidate, classLoader);

            handles.put(moduleId, handle);
            environment.exportRegistry().register(moduleId, exports, classLoader);

            diagnosticsBuilder.append(ModuleLoadDiagnostic.loaded(moduleId, version));

        }

        return diagnosticsBuilder.build();

    }

    private void handleStartError(ModuleHandle handle) {
        if (handle instanceof SimpleModuleHandle simpleHandle) {
            simpleHandle.setContext(null);
            simpleHandle.setState(ModuleState.START_FAILED);
        }
    }

    private void start(ModuleHandle handle) throws
            RequireDependencyException,
            IllegalStateException,
            ContextBuildException,
            ContextStartException,
            MeshRegisterException
    {

        ModuleState currentState = handle.state();
        if (currentState != ModuleState.LOADED && currentState != ModuleState.STOPPED)
            throw new IllegalStateException("Required LOADED or STOPPED state to start the module. Actual: " + currentState + ".");

        ModuleDescriptor descriptor = handle.descriptor();
        List<ModuleHandle> dependencyHandles = getDependencyHandles(descriptor.id());
        boolean dependenciesStarted = dependencyHandles.stream()
                .allMatch(dependency -> dependency.state() == ModuleState.STARTED);

        if (!dependenciesStarted) {
            List<ModuleHandle> notStartedDependencies = dependencyHandles.stream()
                    .filter(dependency -> dependency.state() != ModuleState.STARTED)
                    .toList();

            throw new RequireDependencyException(
                    "Dependencies have not started: " +
                            String.join(
                                    ", ",
                                    notStartedDependencies.stream().map(dependency -> dependency.descriptor().id()).toList()
                            ),
                    notStartedDependencies
            );
        }

        SimpleModuleHandle simpleHandle = (SimpleModuleHandle) handle;
        simpleHandle.setState(ModuleState.STARTING);


        Context moduleContext = SimpleContextBootstrap.bootstrap(handle.classLoader())
                .id(descriptor.id())
                .build();

        List<String> sees = descriptor.getVersionedDependencies().stream()
                .map(VersionedDependencyRequest::key)
                .toList();

        DagContextMesh.DagMeshRegistration registration = new DagContextMesh.DagMeshRegistration(moduleContext, sees);
        Context meshViewContext = contextMesh.register(registration);
        meshViewContext.start();

        simpleHandle.setContext(meshViewContext);
        simpleHandle.setState(ModuleState.STARTED);

    }

    @Override
    public void start(String id) throws ModuleStartException {
        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        ModuleHandle handle = getHandle(id);

        boolean success = false;
        try {
            start(handle);
            success = true;

        } catch (RequireDependencyException ex) {
            throw new ModuleStartException("Module requires all its dependencies to be started.", ex);

        } catch (IllegalStateException ex) {
            throw new ModuleStartException("Starting module from incorrect state.", ex);

        } catch (ContextBuildException ex) {
            throw new ModuleStartException("Failed to bootstrap module context from class loader.", ex);

        } catch (ContextStartException ex) {
            throw new ModuleStartException("Failed to start module context.", ex);

        } catch (MeshRegisterException ex) {
            throw new ModuleStartException("Failed to register module in a context mesh.", ex);

        } finally {
            if (!success) handleStartError(handle);

        }

    }

    @Override
    public Diagnostics<ModuleStartDiagnostic> startAll() {

        DiagnosticsBuilder<ModuleStartDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        int started = 0;

        for (ModuleHandle handle : getHandles()) {

            ModuleDescriptor descriptor = handle.descriptor();
            String moduleId = descriptor.id();
            SemVersion version = descriptor.version();

            ModuleState state = handle.state();
            if (state != ModuleState.LOADED && state != ModuleState.STOPPED) continue;

            boolean success = false;
            try {
                start(handle);
                success = true;
                started++;

                diagnosticsBuilder.append(ModuleStartDiagnostic.started(moduleId, version));

            } catch (RequireDependencyException ex) {
                diagnosticsBuilder.append(ModuleStartDiagnostic.requireDependencies(moduleId, ex.getDependencyHandles()));

            } catch (ContextStartException ex) {
                diagnosticsBuilder.append(ModuleStartDiagnostic.bootstrapFailed(moduleId));

            } catch (MeshRegisterException ex) {
                diagnosticsBuilder.append(ModuleStartDiagnostic.contextNotStarted(moduleId));

            } catch (ContextBuildException ex) {
                diagnosticsBuilder.append(ModuleStartDiagnostic.meshRegistrationFailed(moduleId));

            } finally {
                if (!success) handleStartError(handle);

            }

        }

        if (started > 0) {
            diagnosticsBuilder.append(ModuleStartDiagnostic.startedN(started));

        } else {
            diagnosticsBuilder.append(ModuleStartDiagnostic.nothingStarted());

        }

        return diagnosticsBuilder.build();
        
    }

    private void handleStopError(ModuleHandle handle) {
        if (handle instanceof SimpleModuleHandle simpleHandle) {
            simpleHandle.setContext(null);
            simpleHandle.setState(ModuleState.STOP_FAILED);
        }
    }

    private boolean stop(ModuleHandle handle, DiagnosticsBuilder<ModuleStopDiagnostic> diagnosticsBuilder) {

        ModuleDescriptor descriptor = handle.descriptor();
        String moduleId = descriptor.id();
        SemVersion version = descriptor.version();

        if (handle.state() != ModuleState.STARTED) {
            diagnosticsBuilder.append(ModuleStopDiagnostic.notStarted(moduleId));
            return false;
        }

        SimpleModuleHandle simpleHandle = (SimpleModuleHandle) handle;
        simpleHandle.setState(ModuleState.STOPPING);

        Context context = simpleHandle.context();
        String contextId = context.getId();

        boolean success = false;
        try {
            contextMesh.planUnregister(contextId, MeshContextSelectionStrategies.CASCADE).execute();
            context.dispose();

            simpleHandle.setState(ModuleState.STOPPED);
            simpleHandle.setContext(null);

            success = true;

        } catch (MeshUnregisterExecuteException ex) {
            diagnosticsBuilder.append(ModuleStopDiagnostic.meshUnregisterExecuteFailed(moduleId));

        } catch (MeshContextSelectionException ex) {
            diagnosticsBuilder.append(ModuleStopDiagnostic.meshUnregisterPlanFailed(moduleId));

        }

        if (!success) {
            handleStopError(simpleHandle);
            return false;
        }

        diagnosticsBuilder.append(ModuleStopDiagnostic.stopped(moduleId, version));

        return true;
    }

    @Override
    public ModuleBatchCommand<Diagnostics<ModuleStopDiagnostic>> stop(String id, ModuleSelectionStrategy strategy) throws ModuleStopException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStop = strategy.select(id, this);

            return new AbstractModuleBatchCommand<>(toStop) {
                @Override
                public Diagnostics<ModuleStopDiagnostic> confirm() {
                    DiagnosticsBuilder<ModuleStopDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

                    int stopped = 0;

                    for (ModuleHandle handle : handles) {

                        if (!stop(handle, diagnosticsBuilder)) continue;
                        stopped++;

                    }

                    if (stopped > 0) {
                        diagnosticsBuilder.append(ModuleStopDiagnostic.stoppedN(stopped));

                    } else {
                        diagnosticsBuilder.append(ModuleStopDiagnostic.nothingStopped());

                    }

                    return diagnosticsBuilder.build();
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleStopException("Failed to select module group", ex);

        }

    }

    private void handleUnloadError(ModuleHandle handle) {
        if (handle instanceof SimpleModuleHandle simpleHandle) {
            simpleHandle.setContext(null);
            simpleHandle.setState(ModuleState.UNLOAD_FAILED);
        }
    }

    private CompletableFuture<ModuleUnloadGCReport> unload(ModuleHandle handle, DiagnosticsBuilder<ModuleUnloadDiagnostic> diagnosticsBuilder) {

        ModuleDescriptor descriptor = handle.descriptor();
        String moduleId = descriptor.id();
        SemVersion version = descriptor.version();

        ModuleState state = handle.state();
        if (state != ModuleState.STOPPED && state != ModuleState.LOADED) {
            diagnosticsBuilder.append(ModuleUnloadDiagnostic.incorrectState(moduleId));

            return CompletableFuture.completedFuture(ModuleUnloadGCReport.notUnloaded(moduleId));
        }

        SimpleModuleHandle simpleHandle = (SimpleModuleHandle) handle;
        simpleHandle.setState(ModuleState.UNLOADING);

        boolean success = false;
        try {
            dependencyResolver.state().forget(moduleId);
            environment.exportRegistry().unregister(moduleId);
            handles.remove(moduleId);

            success = true;

        } catch (ResolverForgetException ex) {
            diagnosticsBuilder.append(ModuleUnloadDiagnostic.forgetFailed(moduleId, ex.getDependents()));

        }

        if (!success) {
            handleUnloadError(simpleHandle);

            return CompletableFuture.completedFuture(ModuleUnloadGCReport.notUnloaded(moduleId));

        }

        ClassLoader unloadedClassLoader = handle.classLoader();
        simpleHandle.setClassLoader(null);

        leakDetector.track(moduleId, unloadedClassLoader);


        diagnosticsBuilder.append(ModuleUnloadDiagnostic.unloaded(moduleId, version));

        return leakDetector.awaitUnloadAsync(moduleId, Duration.of(5, ChronoUnit.SECONDS))
                .thenApply(_ -> ModuleUnloadGCReport.success(moduleId))
                .exceptionally(ex -> ModuleUnloadGCReport.leaked(moduleId, ex));

    }

    @Override
    public ModuleBatchCommand<ModuleUnloadResult> unload(String id, ModuleSelectionStrategy strategy) throws ModuleUnloadException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toUnload = strategy.select(id, this);

            return new AbstractModuleBatchCommand<>(toUnload) {

                @Override
                public ModuleUnloadResult confirm() {

                    DiagnosticsBuilder<ModuleUnloadDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();
                    List<CompletableFuture<ModuleUnloadGCReport>> futures = new ArrayList<>();

                    int unloaded = 0;

                    for (ModuleHandle handle : handles) {

                        CompletableFuture<ModuleUnloadGCReport> reportFuture = unload(handle, diagnosticsBuilder);
                        futures.add(reportFuture);

                        unloaded++;

                    }

                    if (unloaded > 0) {
                        diagnosticsBuilder.append(ModuleUnloadDiagnostic.unloadedN(unloaded));

                    } else {
                        diagnosticsBuilder.append(ModuleUnloadDiagnostic.nothingUnloaded());

                    }

                    CompletableFuture<List<ModuleUnloadGCReport>> gcFuture =
                            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                                    .thenApply(_ -> futures.stream().map(CompletableFuture::join).toList());

                    return new SimpleModuleUnloadResult(
                            diagnosticsBuilder.build(),
                            gcFuture
                    );
                }

            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleUnloadException("Failed to select module group", ex);

        }

    }

}
