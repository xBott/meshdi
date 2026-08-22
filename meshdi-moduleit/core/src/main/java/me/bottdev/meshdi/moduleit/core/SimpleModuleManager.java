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
import me.bottdev.meshdi.moduleit.api.diagnostic.*;
import me.bottdev.meshdi.moduleit.api.exceptions.*;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SimpleModuleManager implements ModuleManager {

    private final StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver;
    private final ModuleLoadEnvironment environment;
    private final ModuleClassLoaderLeakDetector leakDetector;

    private final LinkedHashMap<String, SimpleModuleHandle> handles = new LinkedHashMap<>();
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

    private boolean start(
            ModuleHandle handle,
            DiagnosticsBuilder<ModuleStartDiagnostic> diagnosticsBuilder
    ) {

        ModuleDescriptor descriptor = handle.descriptor();
        String moduleId = descriptor.id();
        SemVersion version = descriptor.version();

        ModuleState currentState = handle.state();
        if (currentState != ModuleState.LOADED && currentState != ModuleState.STOPPED) {
            diagnosticsBuilder.append(ModuleStartDiagnostic.incorrectState(moduleId, currentState));
            return false;
        }

        SimpleModuleHandle simpleHandle = (SimpleModuleHandle) handle;
        simpleHandle.setState(ModuleState.STARTING);

        boolean success = false;
        try {
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

            success = true;

        } catch (ContextBuildException ex) {
            diagnosticsBuilder.append(ModuleStartDiagnostic.bootstrapFailed(moduleId, ex));

        } catch (ContextStartException ex) {
            diagnosticsBuilder.append(ModuleStartDiagnostic.contextNotStarted(moduleId, ex));

        } catch (MeshRegisterException ex) {
            diagnosticsBuilder.append(ModuleStartDiagnostic.meshRegistrationFailed(moduleId, ex));

        }

        if (!success) {
            handleStartError(simpleHandle);
            return false;
        }

        diagnosticsBuilder.append(ModuleStartDiagnostic.started(moduleId, version));

        return true;

    }

    private Diagnostics<ModuleStartDiagnostic> startBatch(List<ModuleHandle> toStart) {

        DiagnosticsBuilder<ModuleStartDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        int started = 0;
        for (ModuleHandle handle : toStart) {
            if (start(handle, diagnosticsBuilder)) started++;

        }

        if (started > 0) {
            diagnosticsBuilder.append(ModuleStartDiagnostic.startedN(started));

        } else {
            diagnosticsBuilder.append(ModuleStartDiagnostic.nothingStarted());

        }

        return diagnosticsBuilder.build();

    }

    @Override
    public ModuleBatchCommand<Diagnostics<ModuleStartDiagnostic>> start(String id, StartModuleSelector selector) throws
            ModuleStartException
    {
        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStart = selector.selectStart(id, this);

            return new AbstractModuleBatchCommand<>(toStart) {
                @Override
                public Diagnostics<ModuleStartDiagnostic> confirm() {
                    return startBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleStartException("Failed to select module group.", ex);

        }

    }

    @Override
    public Diagnostics<ModuleStartDiagnostic> startAll() {
        return startBatch(getHandles());
    }

    private void handleStopError(ModuleHandle handle) {
        if (handle instanceof SimpleModuleHandle simpleHandle) {
            simpleHandle.setContext(null);
            simpleHandle.setState(ModuleState.STOP_FAILED);
        }
    }

    private boolean stop(
            ModuleHandle handle,
            DiagnosticsBuilder<ModuleStopDiagnostic> diagnosticsBuilder
    ) {

        ModuleDescriptor descriptor = handle.descriptor();
        String moduleId = descriptor.id();
        SemVersion version = descriptor.version();

        if (handle.state() != ModuleState.STARTED) {
            diagnosticsBuilder.append(ModuleStopDiagnostic.incorrectState(moduleId));
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
            diagnosticsBuilder.append(ModuleStopDiagnostic.meshUnregisterExecuteFailed(moduleId, ex));

        } catch (MeshContextSelectionException ex) {
            diagnosticsBuilder.append(ModuleStopDiagnostic.meshUnregisterPlanFailed(moduleId, ex));

        }

        if (!success) {
            handleStopError(simpleHandle);
            return false;
        }

        diagnosticsBuilder.append(ModuleStopDiagnostic.stopped(moduleId, version));

        return true;
    }

    private Diagnostics<ModuleStopDiagnostic> stopBatch(List<ModuleHandle> toStop) {

        DiagnosticsBuilder<ModuleStopDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        int stopped = 0;
        for (ModuleHandle handle : toStop) {
            if (stop(handle, diagnosticsBuilder)) stopped++;

        }

        if (stopped > 0) {
            diagnosticsBuilder.append(ModuleStopDiagnostic.stoppedN(stopped));

        } else {
            diagnosticsBuilder.append(ModuleStopDiagnostic.nothingStopped());

        }

        return diagnosticsBuilder.build();

    }

    @Override
    public ModuleBatchCommand<Diagnostics<ModuleStopDiagnostic>> stop(String id, StopModuleSelector selector)
            throws ModuleStopException
    {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStop = selector.selectStop(id, this);

            return new AbstractModuleBatchCommand<>(toStop) {
                @Override
                public Diagnostics<ModuleStopDiagnostic> confirm() {
                    return stopBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleStopException("Failed to select module group.", ex);

        }

    }

    @Override
    public Diagnostics<ModuleStopDiagnostic> stopAll() {
        return stopBatch(getHandles().reversed());
    }

    private void handleUnloadError(ModuleHandle handle) {
        if (handle instanceof SimpleModuleHandle simpleHandle) {
            simpleHandle.setContext(null);
            simpleHandle.setState(ModuleState.UNLOAD_FAILED);
        }
    }

    private CompletableFuture<ModuleUnloadGCReport> unload(
            ModuleHandle handle,
            DiagnosticsBuilder<ModuleUnloadDiagnostic> diagnosticsBuilder
    ) {

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

    private ModuleUnloadResult unloadBatch(List<ModuleHandle> toUnload) {
        DiagnosticsBuilder<ModuleUnloadDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();
        List<CompletableFuture<ModuleUnloadGCReport>> futures = new ArrayList<>();

        int unloaded = 0;

        for (ModuleHandle handle : toUnload) {

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

    @Override
    public ModuleBatchCommand<ModuleUnloadResult> unload(String id, StopModuleSelector selector) throws ModuleUnloadException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toUnload = selector.selectStop(id, this);

            return new AbstractModuleBatchCommand<>(toUnload) {

                @Override
                public ModuleUnloadResult confirm() {
                    return unloadBatch(handles);
                }

            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleUnloadException("Failed to select module group.", ex);

        }

    }

    @Override
    public ModuleUnloadResult unloadAll() {
        return unloadBatch(getHandles().reversed());
    }

    private void restartStopAll(
            List<ModuleHandle> toStop,
            DiagnosticsBuilder<ModuleRestartDiagnostic> diagnosticsBuilder
    ) {

        Diagnostics<ModuleStopDiagnostic> diagnostics = stopBatch(toStop);
        diagnostics.forEach(stopDiagnostic -> {

            ModuleRestartDiagnostic restartDiagnostic = switch (stopDiagnostic) {
                case ModuleStopDiagnostic.IncorrectState notStarted ->
                        ModuleRestartDiagnostic.incorrectState(notStarted.id());
                case ModuleStopDiagnostic.MeshUnregisterPlanFailed planFailed ->
                        ModuleRestartDiagnostic.meshUnregisterPlanFailed(planFailed.id(), planFailed.error());
                case ModuleStopDiagnostic.MeshUnregisterExecutionFailed executionFailed ->
                        ModuleRestartDiagnostic.meshUnregisterExecuteFailed(executionFailed.id(), executionFailed.error());
                case ModuleStopDiagnostic.ForgetFailed forgetFailed ->
                        ModuleRestartDiagnostic.forgetFailed(forgetFailed.id(), forgetFailed.dependents());
                default -> null;
            };
            if (restartDiagnostic != null) diagnosticsBuilder.append(restartDiagnostic);

        });

    }

    private void restartStartAll(
            List<ModuleHandle> toStart,
            DiagnosticsBuilder<ModuleRestartDiagnostic> diagnosticsBuilder
    ) {

        Diagnostics<ModuleStartDiagnostic> diagnostics = startBatch(toStart);
        diagnostics.forEach(startDiagnostic -> {

            ModuleRestartDiagnostic restartDiagnostic = switch (startDiagnostic) {
                case ModuleStartDiagnostic.BootstrapFailed bootstrapFailed ->
                        ModuleRestartDiagnostic.bootstrapFailed(bootstrapFailed.id(), bootstrapFailed.error());
                case ModuleStartDiagnostic.ContextNotStarted notStarted ->
                        ModuleRestartDiagnostic.contextNotStarted(notStarted.id(), notStarted.error());
                case ModuleStartDiagnostic.MeshRegistrationFailed registrationFailed ->
                        ModuleRestartDiagnostic.meshRegistrationFailed(registrationFailed.id(), registrationFailed.error());
                case ModuleStartDiagnostic.Started started ->
                        ModuleRestartDiagnostic.restarted(started.id(), started.version());
                case ModuleStartDiagnostic.StartedN startedN ->
                        ModuleRestartDiagnostic.restartedN(startedN.amount());
                case ModuleStartDiagnostic.NothingStarted _ ->
                        ModuleRestartDiagnostic.nothingRestarted();
                default -> null;
            };
            if (restartDiagnostic != null) diagnosticsBuilder.append(restartDiagnostic);

        });

    }

    private Diagnostics<ModuleRestartDiagnostic> restartBatch(List<ModuleHandle> toRestart) {
        DiagnosticsBuilder<ModuleRestartDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        restartStopAll(toRestart, diagnosticsBuilder);
        restartStartAll(toRestart.reversed(), diagnosticsBuilder);

        return diagnosticsBuilder.build();
    }

    @Override
    public ModuleBatchCommand<Diagnostics<ModuleRestartDiagnostic>> restart(String id, StopModuleSelector selector)
            throws ModuleRestartException
    {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toRestart = selector.selectStop(id, this);

            return new AbstractModuleBatchCommand<>(toRestart) {
                @Override
                public Diagnostics<ModuleRestartDiagnostic> confirm() {
                    return restartBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleRestartException("Failed to select module group.", ex);

        }


    }

    @Override
    public Diagnostics<ModuleRestartDiagnostic> restartAll() {
        return restartBatch(getHandles().reversed());
    }

}
