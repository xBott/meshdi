package me.bottdev.meshdi.moduleit.core;

import lombok.Builder;
import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.commons.diagnostic.DiagnosticsBuilder;
import me.bottdev.kern.commons.diagnostic.ListDiagnostics;
import me.bottdev.kern.commons.wrapper.DiagnosticResult;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.dependency.DependentContainer;
import me.bottdev.kern.dependency.ResolutionResult;
import me.bottdev.kern.dependency.StatefulDependencyResolver;
import me.bottdev.kern.dependency.containers.SimpleDependentContainer;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.VersionRange;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.diagnostic.*;
import me.bottdev.meshdi.moduleit.api.exceptions.*;
import me.bottdev.meshdi.moduleit.api.LeakDetectorResult;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProviderContainer;
import me.bottdev.meshdi.moduleit.core.classprovider.ApiClassProvider;
import me.bottdev.meshdi.moduleit.core.classprovider.ExportRegistryClassProvider;
import me.bottdev.meshdi.moduleit.core.classprovider.IsolatedLibraryClassProvider;
import me.bottdev.meshdi.moduleit.core.classprovider.PlatformClassProvider;
import me.bottdev.meshdi.moduleit.core.classprovider.SharedLibraryClassProvider;
import java.net.URLClassLoader;

public class SimpleModuleManager implements ModuleManager {

    private final StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver;
    private final ModuleLoadEnvironment environment;
    private final ModuleLibraryLoader libraryLoader;
    private final ContextMesh contextMesh;
    private final ModuleClassLoaderLeakDetector leakDetector;

    private final LinkedHashMap<String, InternalModuleHandle> handles = new LinkedHashMap<>();
    private final Set<Path> sharedLibraries = new HashSet<>();
    private URLClassLoader sharedLibraryLoader = new URLClassLoader(new URL[0], null);

    @Builder
    public SimpleModuleManager(
            @NonNull @Dependency(qualifier = "moduleDependencyResolver")
            StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver,
            @NonNull ModuleLoadEnvironment environment,
            @NonNull ModuleLibraryLoader libraryLoader,
            @NonNull ContextMesh contextMesh,
            @NonNull ModuleClassLoaderLeakDetector leakDetector
    ) {
        this.environment = environment;
        this.dependencyResolver = dependencyResolver;
        this.libraryLoader = libraryLoader;
        this.contextMesh = contextMesh;
        this.leakDetector = leakDetector;
    }

    ContextMesh contextMesh() { return contextMesh; }
    StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver() { return dependencyResolver; }
    void removeHandle(String id) { handles.remove(id); }

    public void addSharedLibraries(Collection<Path> paths) {
        sharedLibraries.addAll(paths);
        rebuildSharedLibraryLoader();
    }

    private void rebuildSharedLibraryLoader() {
        if (sharedLibraryLoader != null) {
            try {
                sharedLibraryLoader.close();
            } catch (IOException ignored) {}
        }
        
        List<URL> validUrls = new ArrayList<>();
        for (Path path : sharedLibraries) {
            try {
                validUrls.add(path.toUri().toURL());
            } catch (MalformedURLException ignored) {}
        }
        
        this.sharedLibraryLoader = new URLClassLoader(validUrls.toArray(new URL[0]), null);
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

    private Collection<ModuleCandidate> resolveUniqueCandidates(
            List<ModuleCandidate> candidates,
            DiagnosticsBuilder<ModuleResolutionDiagnostic> diagnosticsBuilder
    ) {

        Map<String, ModuleCandidate> uniqueCandidates = new HashMap<>();

        for (ModuleCandidate candidate : candidates) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            VersionRange requiredApiVersion = descriptor.apiVersion();

            if (exists(moduleId)) {
                diagnosticsBuilder.append(new ModuleResolutionDiagnostic.AlreadyLoaded(moduleId));
                continue;
            }

            if (uniqueCandidates.containsKey(moduleId)) {
                diagnosticsBuilder.append(new ModuleResolutionDiagnostic.Duplicate(moduleId));
                continue;
            }

            if (!requiredApiVersion.satisfies(environment.apiVersion())) {
                diagnosticsBuilder.append(new ModuleResolutionDiagnostic.ApiVersionMismatch(
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

    private DiagnosticResult<ResolutionResult<String, ModuleCandidate>, ModuleResolutionDiagnostic> resolveDependencies(
            Collection<ModuleCandidate> uniqueCandidates,
            DiagnosticsBuilder<ModuleResolutionDiagnostic> diagnosticsBuilder
    ) {

        DependentContainer<String, ModuleCandidate> container = SimpleDependentContainer.<String, ModuleCandidate>builder()
                .add(uniqueCandidates)
                .build();

        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, DependencyDiagnostic> diagnosticResult =
                dependencyResolver.resolveAndRemember(container);

        if (diagnosticResult.isPresent()) {
            return DiagnosticResult.success(diagnosticResult.unwrap(), diagnosticsBuilder.build());

        } else {
            diagnosticsBuilder.append(new ModuleResolutionDiagnostic.BadResolution(diagnosticResult.unwrapDiagnostics()));
            return DiagnosticResult.failure(diagnosticsBuilder.build());

        }

    }

    @Override
    public Diagnostics<ModuleResolutionDiagnostic> resolve(ModuleRepository repository) throws CandidateListException {

        List<ModuleCandidate> candidates = repository.candidates();

        DiagnosticsBuilder<ModuleResolutionDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        Collection<ModuleCandidate> uniqueCandidates = resolveUniqueCandidates(candidates, diagnosticsBuilder);
        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, ModuleResolutionDiagnostic> diagnosticResult =
                resolveDependencies(uniqueCandidates, diagnosticsBuilder);

        if (!diagnosticResult.isPresent()) {
            return diagnosticResult.unwrapDiagnostics();
        }

        ResolutionResult<String, ModuleCandidate> resolutionResult = diagnosticResult.unwrap();

        for (ModuleCandidate candidate : resolutionResult.ordered()) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            SemVersion version = descriptor.version();

            InternalModuleHandle handle;
            if (candidate instanceof InternalModuleCandidate internalCandidate) {
                handle = internalCandidate.createHandle();
            } else {
                handle = SimpleModuleHandle.ofResolved(candidate);
            }
            
            handles.put(moduleId, handle);

            diagnosticsBuilder.append(new ModuleResolutionDiagnostic.Resolved(moduleId, version));

        }

        return diagnosticsBuilder.build();
    }

    private CompletableFuture<Diagnostics<LibraryLoadDiagnostic>> prepareBatch(List<ModuleHandle> toPrepare) {

        List<ModuleHandle> resolved = toPrepare.stream()
                .filter(handle -> handle.state() == ModuleState.RESOLVED)
                .toList();

        return libraryLoader.loadAll(resolved).thenApply(result -> {

            boolean hasErrors = result.diagnostics().has(DiagnosticType.ERROR);

            if (hasErrors) {
                return result.diagnostics();
            }

            addSharedLibraries(result.sharedLibraries());

            for (ModuleHandle handle : resolved) {
                InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
                List<Path> libraries = result.isolatedLibraries().getOrDefault(handle, List.of());
                internalHandle.completePreparation(libraries);
            }

            return result.diagnostics();

        });

    }

    @Override
    public ModuleBatchCommand<CompletableFuture<Diagnostics<LibraryLoadDiagnostic>>> prepare(
            String id,
            DependencyModuleSelector selector
    ) throws ModulePrepareException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {
            List<ModuleHandle> toPrepare = selector.selectDependencies(id, this);

            return new AbstractModuleBatchCommand<>(toPrepare) {
                @Override
                public CompletableFuture<Diagnostics<LibraryLoadDiagnostic>> confirm() {
                    return prepareBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModulePrepareException("Failed to select module group.", ex);

        }

    }

    @Override
    public CompletableFuture<Diagnostics<LibraryLoadDiagnostic>> prepareAll() {
        return prepareBatch(getHandles());
    }

    ModuleClassLoader createClassLoader(ModuleHandle handle) throws MalformedURLException {
        ModuleDescriptor descriptor = handle.descriptor();
        String moduleId = descriptor.id();

        List<URL> isolatedUrls = new ArrayList<>();
        for (Path libPath : handle.libraries()) {
            isolatedUrls.add(libPath.toUri().toURL());
        }

        List<ClassProvider> providers = new ArrayList<>();
        providers.add(new PlatformClassProvider());
        providers.add(new ApiClassProvider(environment.apiLoader(), environment.apiPackages()));
        providers.add(new ExportRegistryClassProvider(moduleId, environment.exportRegistry()));
        providers.add(new SharedLibraryClassProvider(sharedLibraryLoader));
        providers.add(new IsolatedLibraryClassProvider(isolatedUrls.toArray(new URL[0])));

        List<String> dependencyIds = descriptor.getVersionedDependencies().stream()
                .map(VersionedDependencyRequest::key)
                .toList();

        return new ModuleClassLoader(
                moduleId,
                new URL[]{ handle.candidate().sourceUrl() },
                new ClassProviderContainer(providers),
                dependencyIds
        );
    }


    private Diagnostics<ModuleLoadDiagnostic> loadBatch(List<ModuleHandle> toLoad) {

        DiagnosticsBuilder<ModuleLoadDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();
        int loaded = 0;

        for (ModuleHandle handle : toLoad) {
            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            if (internalHandle.doLoad(this, diagnosticsBuilder)) {
                loaded++;
            }
        }

        if (loaded > 0) {
            diagnosticsBuilder.append(new ModuleLoadDiagnostic.LoadedN(loaded));
        } else {
            diagnosticsBuilder.append(new ModuleLoadDiagnostic.NothingLoaded());
        }

        return diagnosticsBuilder.build();
    }

    @Override
    public ModuleBatchCommand<Diagnostics<ModuleLoadDiagnostic>> load(String id, DependencyModuleSelector selector) throws ModuleLoadException {
        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {
            List<ModuleHandle> toLoad = selector.selectDependencies(id, this);

            return new AbstractModuleBatchCommand<>(toLoad) {
                @Override
                public Diagnostics<ModuleLoadDiagnostic> confirm() {
                    return loadBatch(toLoad);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleLoadException("Failed to select module group.", ex);
        }
    }

    @Override
    public Diagnostics<ModuleLoadDiagnostic> loadAll() {
        return loadBatch(getHandles());
    }


    private Diagnostics<ModuleStartDiagnostic> startBatch(List<ModuleHandle> toStart) {

        DiagnosticsBuilder<ModuleStartDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        int started = 0;
        for (ModuleHandle handle : toStart) {
            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            if (internalHandle.doStart(this, diagnosticsBuilder)) started++;

        }

        if (started > 0) {
            diagnosticsBuilder.append(new ModuleStartDiagnostic.StartedN(started));

        } else {
            diagnosticsBuilder.append(new ModuleStartDiagnostic.NothingStarted());

        }

        return diagnosticsBuilder.build();

    }

    @Override
    public ModuleBatchCommand<Diagnostics<ModuleStartDiagnostic>> start(String id, DependencyModuleSelector selector) throws
            ModuleStartException
    {
        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStart = selector.selectDependencies(id, this);

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


    private Diagnostics<ModuleStopDiagnostic> stopBatch(List<ModuleHandle> toStop) {

        DiagnosticsBuilder<ModuleStopDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        int stopped = 0;
        for (ModuleHandle handle : toStop) {
            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            if (internalHandle.doStop(this, diagnosticsBuilder)) stopped++;

        }

        if (stopped > 0) {
            diagnosticsBuilder.append(new ModuleStopDiagnostic.StoppedN(stopped));

        } else {
            diagnosticsBuilder.append(new ModuleStopDiagnostic.NothingStopped());

        }

        return diagnosticsBuilder.build();

    }

    @Override
    public ModuleBatchCommand<Diagnostics<ModuleStopDiagnostic>> stop(String id, DependentModuleSelector selector)
            throws ModuleStopException
    {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStop = selector.selectDependents(id, this);

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


    private CompletableFuture<Diagnostics<ModuleUnloadDiagnostic>> unloadBatch(List<ModuleHandle> toUnload) {
        DiagnosticsBuilder<ModuleUnloadDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        int unloaded = 0;

        for (ModuleHandle handle : toUnload) {
            String moduleId = handle.descriptor().id();

            if (handle.isPersistent()) {
                diagnosticsBuilder.append(new ModuleUnloadDiagnostic.SkippedPersistent(moduleId));
                continue;
            }

            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            CompletableFuture<Void> future = internalHandle.doUnload(this, diagnosticsBuilder)
                    .thenAccept(result -> {
                        switch (result) {
                            case LeakDetectorResult.Freed _ ->
                                    diagnosticsBuilder.append(new ModuleUnloadDiagnostic.Freed(moduleId));
                            case LeakDetectorResult.Disabled _ ->
                                    diagnosticsBuilder.append(new ModuleUnloadDiagnostic.LeakCheckDisabled(moduleId));
                            case LeakDetectorResult.Leaked leaked ->
                                    diagnosticsBuilder.append(new ModuleUnloadDiagnostic.Leaked(moduleId, leaked.error()));
                        }
                    });
            futures.add(future);

            unloaded++;

        }

        if (unloaded > 0) {
            diagnosticsBuilder.append(new ModuleUnloadDiagnostic.UnloadedN(unloaded));

        } else {
            diagnosticsBuilder.append(new ModuleUnloadDiagnostic.NothingUnloaded());

        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .thenApply(_ -> diagnosticsBuilder.build());
    }

    @Override
    public ModuleBatchCommand<CompletableFuture<Diagnostics<ModuleUnloadDiagnostic>>> unload(
            String id,
            DependentModuleSelector selector
    ) throws ModuleUnloadException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toUnload = selector.selectDependents(id, this);

            return new AbstractModuleBatchCommand<>(toUnload) {

                @Override
                public CompletableFuture<Diagnostics<ModuleUnloadDiagnostic>> confirm() {
                    return unloadBatch(handles);
                }

            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleUnloadException("Failed to select module group.", ex);

        }

    }

    @Override
    public CompletableFuture<Diagnostics<ModuleUnloadDiagnostic>> unloadAll() {
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
                        new ModuleRestartDiagnostic.IncorrectState(notStarted.id());
                case ModuleStopDiagnostic.MeshUnregisterPlanFailed planFailed ->
                        new ModuleRestartDiagnostic.MeshUnregisterPlanFailed(planFailed.id(), planFailed.error());
                case ModuleStopDiagnostic.MeshUnregisterExecutionFailed executionFailed ->
                        new ModuleRestartDiagnostic.MeshUnregisterExecutionFailed(executionFailed.id(), executionFailed.error());
                case ModuleStopDiagnostic.ForgetFailed forgetFailed ->
                        new ModuleRestartDiagnostic.ForgetFailed(forgetFailed.id(), forgetFailed.dependents());
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
                        new ModuleRestartDiagnostic.BootstrapFailed(bootstrapFailed.id(), bootstrapFailed.error());
                case ModuleStartDiagnostic.ContextNotStarted notStarted ->
                        new ModuleRestartDiagnostic.ContextNotStarted(notStarted.id(), notStarted.error());
                case ModuleStartDiagnostic.MeshRegistrationFailed registrationFailed ->
                        new ModuleRestartDiagnostic.MeshRegistrationFailed(registrationFailed.id(), registrationFailed.error());
                case ModuleStartDiagnostic.Started started ->
                        new ModuleRestartDiagnostic.Restarted(started.id(), started.version());
                case ModuleStartDiagnostic.StartedN startedN ->
                        new ModuleRestartDiagnostic.RestartedN(startedN.amount());
                case ModuleStartDiagnostic.NothingStarted _ ->
                        new ModuleRestartDiagnostic.NothingRestarted();
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
    public ModuleBatchCommand<Diagnostics<ModuleRestartDiagnostic>> restart(String id, DependentModuleSelector selector)
            throws ModuleRestartException
    {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toRestart = selector.selectDependents(id, this);

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
