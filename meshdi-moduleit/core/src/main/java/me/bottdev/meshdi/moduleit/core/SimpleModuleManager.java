package me.bottdev.meshdi.moduleit.core;

import lombok.Builder;
import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.*;
import me.bottdev.kern.commons.wrapper.DiagnosticResult;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.dependency.DependentContainer;
import me.bottdev.kern.dependency.ResolutionResult;
import me.bottdev.kern.dependency.containers.SimpleDependentContainer;
import me.bottdev.kern.dependency.versioned.StatefulVersionedDependencyResolver;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
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
import me.bottdev.meshdi.moduleit.core.classprovider.IsolatedLibraryClassProvider;
import org.semver4j.Semver;
import org.semver4j.range.RangeList;

import java.net.URLClassLoader;

public class SimpleModuleManager implements ModuleManager {

    private final StatefulVersionedDependencyResolver<String, ModuleCandidate> dependencyResolver;
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
            StatefulVersionedDependencyResolver<String, ModuleCandidate> dependencyResolver,
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
    StatefulVersionedDependencyResolver<String, ModuleCandidate> dependencyResolver() { return dependencyResolver; }
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

    private ModuleBatchResult getModuleBatchResult(int total, int proceed) {
        int failed = total - proceed;
        if (proceed == 0 && total != 0) return new ModuleBatchResult.Failed(failed);
        if (failed > 0) return new ModuleBatchResult.PartialSuccess(proceed, failed);
        return new ModuleBatchResult.Success(proceed);
    }

    private Set<ModuleCandidate> resolveUniqueCandidates(
            List<ModuleCandidate> candidates,
            DiagnosticSink<ModuleResolutionDiagnostic> sink
    ) {

        Map<String, ModuleCandidate> uniqueCandidates = new HashMap<>();

        for (ModuleCandidate candidate : candidates) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            RangeList requiredApiVersion = descriptor.apiVersion();

            if (exists(moduleId)) {
                sink.accept(new ModuleResolutionDiagnostic.AlreadyLoaded(moduleId));
                continue;
            }

            if (uniqueCandidates.containsKey(moduleId)) {
                sink.accept(new ModuleResolutionDiagnostic.Duplicate(moduleId));
                continue;
            }

            if (!environment.apiVersion().satisfies(requiredApiVersion)) {
                sink.accept(new ModuleResolutionDiagnostic.ApiVersionMismatch(
                        moduleId,
                        requiredApiVersion,
                        environment.apiVersion())
                );
                continue;
            }

            uniqueCandidates.put(moduleId, candidate);

        }

        return new HashSet<>(uniqueCandidates.values());
    }

    private Optional<ResolutionResult<String, ModuleCandidate>> resolveDependencies(
            Collection<ModuleCandidate> uniqueCandidates,
            DiagnosticSink<ModuleResolutionDiagnostic> sink
    ) {

        DependentContainer<String, ModuleCandidate> container = SimpleDependentContainer.<String, ModuleCandidate>builder()
                .add(uniqueCandidates)
                .build();

        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, DependencyDiagnostic> diagnosticResult =
                dependencyResolver.resolveAndRemember(container);

        if (diagnosticResult.isPresent()) {
            return Optional.of(diagnosticResult.unwrap());

        } else {
            sink.accept(new ModuleResolutionDiagnostic.BadDependencyResolution(diagnosticResult.unwrapDiagnostics()));
            return Optional.empty();

        }

    }

    @Override
    public ModuleBatchResult resolve(ModuleRepository repository) throws CandidateListException {

        DiagnosticSink<ModuleResolutionDiagnostic> sink = environment.createDiagnosticSink();

        List<ModuleCandidate> candidates = repository.candidates();
        if (candidates.isEmpty()) {
            sink.accept(new ModuleResolutionDiagnostic.NothingResolved());
            return new ModuleBatchResult.Success(0);
        }

        Collection<ModuleCandidate> uniqueCandidates = resolveUniqueCandidates(candidates, sink);
        if (uniqueCandidates.isEmpty()) {
            sink.accept(new ModuleResolutionDiagnostic.NothingResolved());
            return new ModuleBatchResult.Failed(candidates.size());
        }

        Optional<ResolutionResult<String, ModuleCandidate>> diagnosticResult = resolveDependencies(uniqueCandidates, sink);
        if (diagnosticResult.isEmpty()) {
            sink.accept(new ModuleResolutionDiagnostic.NothingResolved());
            return new ModuleBatchResult.Failed(candidates.size());
        }

        ResolutionResult<String, ModuleCandidate> resolutionResult = diagnosticResult.get();
        List<ModuleCandidate> ordered = resolutionResult.ordered();
        int processedCount = ordered.size();

        for (ModuleCandidate candidate : ordered) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            Semver version = descriptor.semver();

            InternalModuleHandle handle;
            if (candidate instanceof InternalModuleCandidate internalCandidate) {
                handle = internalCandidate.createHandle();
            } else {
                handle = SimpleModuleHandle.ofResolved(candidate);
            }
            
            handles.put(moduleId, handle);

            sink.accept(new ModuleResolutionDiagnostic.Resolved(moduleId, version));

        }

        sink.accept(new ModuleResolutionDiagnostic.ResolvedN(processedCount));

        int failedCount = candidates.size() - processedCount;
        return failedCount <= 0 ?
                new ModuleBatchResult.Success(processedCount) :
                new ModuleBatchResult.PartialSuccess(processedCount, failedCount);
    }

    private CompletableFuture<ModuleBatchResult> prepareBatch(List<ModuleHandle> toPrepare) {

        List<ModuleHandle> resolved = toPrepare.stream()
                .filter(handle -> handle.state() == ModuleState.RESOLVED)
                .toList();

        return libraryLoader.loadAll(resolved).thenApply(result -> {

            if (result.failed()) return new ModuleBatchResult.Failed(resolved.size());

            addSharedLibraries(result.sharedLibraries());

            for (ModuleHandle handle : resolved) {
                InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
                List<Path> libraries = result.isolatedLibraries().getOrDefault(handle, List.of());
                internalHandle.completePreparation(libraries);
            }

            return new ModuleBatchResult.Success(resolved.size());

        });

    }

    @Override
    public ModuleBatchCommand<CompletableFuture<ModuleBatchResult>> prepare(
            String id,
            DependencyModuleSelector selector
    ) throws ModulePrepareException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {
            List<ModuleHandle> toPrepare = selector.selectDependencies(id, this);

            return new AbstractModuleBatchCommand<>(toPrepare) {
                @Override
                public CompletableFuture<ModuleBatchResult> confirm() {
                    return prepareBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModulePrepareException("Failed to select module group.", ex);

        }

    }

    @Override
    public CompletableFuture<ModuleBatchResult> prepareAll() {
        return prepareBatch(getHandles());
    }

    ModuleClassLoader createClassLoader(ModuleHandle handle) throws MalformedURLException {
        ModuleDescriptor descriptor = handle.descriptor();
        String moduleId = descriptor.id();

        List<URL> isolatedUrls = new ArrayList<>();
        for (Path libPath : handle.libraries()) {
            isolatedUrls.add(libPath.toUri().toURL());
        }

        List<ClassProvider> providers = new ArrayList<>(environment.createBaseProviders(moduleId, sharedLibraryLoader));
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


    private ModuleBatchResult loadBatch(List<ModuleHandle> toLoad) {

        DiagnosticSink<ModuleLoadDiagnostic> sink = environment.createDiagnosticSink();
        int total = toLoad.size();
        int loaded = 0;

        for (ModuleHandle handle : toLoad) {
            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            if (internalHandle.doLoad(this, sink)) loaded++;
        }

        if (loaded > 0) {
            sink.accept(new ModuleLoadDiagnostic.LoadedN(loaded));
        } else {
            sink.accept(new ModuleLoadDiagnostic.NothingLoaded());
        }

        return getModuleBatchResult(total, loaded);
    }

    @Override
    public ModuleBatchCommand<ModuleBatchResult> load(String id, DependencyModuleSelector selector) throws ModuleLoadException {
        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {
            List<ModuleHandle> toLoad = selector.selectDependencies(id, this);

            return new AbstractModuleBatchCommand<>(toLoad) {
                @Override
                public ModuleBatchResult confirm() {
                    return loadBatch(toLoad);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleLoadException("Failed to select module group.", ex);
        }
    }

    @Override
    public ModuleBatchResult loadAll() {
        return loadBatch(getHandles());
    }


    private ModuleBatchResult startBatch(List<ModuleHandle> toStart) {

        DiagnosticSink<ModuleStartDiagnostic> sink = environment.createDiagnosticSink();
        int total = toStart.size();
        int started = 0;

        for (ModuleHandle handle : toStart) {
            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            if (internalHandle.doStart(this, sink)) started++;

        }

        if (started > 0) {
            sink.accept(new ModuleStartDiagnostic.StartedN(started));

        } else {
            sink.accept(new ModuleStartDiagnostic.NothingStarted());

        }

        return getModuleBatchResult(total, started);

    }

    @Override
    public ModuleBatchCommand<ModuleBatchResult> start(String id, DependencyModuleSelector selector) throws
            ModuleStartException
    {
        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStart = selector.selectDependencies(id, this);

            return new AbstractModuleBatchCommand<>(toStart) {
                @Override
                public ModuleBatchResult confirm() {
                    return startBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleStartException("Failed to select module group.", ex);

        }

    }

    @Override
    public ModuleBatchResult startAll() {
        return startBatch(getHandles());
    }


    private ModuleBatchResult stopBatch(List<ModuleHandle> toStop) {

        DiagnosticSink<ModuleStopDiagnostic> sink = environment.createDiagnosticSink();
        int total = toStop.size();
        int stopped = 0;

        for (ModuleHandle handle : toStop) {
            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            if (internalHandle.doStop(this, sink)) stopped++;

        }

        if (stopped > 0) {
            sink.accept(new ModuleStopDiagnostic.StoppedN(stopped));

        } else {
            sink.accept(new ModuleStopDiagnostic.NothingStopped());

        }

        return getModuleBatchResult(total, stopped);

    }

    @Override
    public ModuleBatchCommand<ModuleBatchResult> stop(String id, DependentModuleSelector selector)
            throws ModuleStopException
    {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStop = selector.selectDependents(id, this);

            return new AbstractModuleBatchCommand<>(toStop) {
                @Override
                public ModuleBatchResult confirm() {
                    return stopBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleStopException("Failed to select module group.", ex);

        }

    }

    @Override
    public ModuleBatchResult stopAll() {
        return stopBatch(getHandles().reversed());
    }


    private CompletableFuture<ModuleBatchResult> unloadBatch(List<ModuleHandle> toUnload) {
        DiagnosticSink<ModuleUnloadDiagnostic> sink = environment.createDiagnosticSink();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        int total = toUnload.size();
        int unloaded = 0;

        for (ModuleHandle handle : toUnload) {
            String moduleId = handle.descriptor().id();

            if (handle.isPersistent()) {
                sink.accept(new ModuleUnloadDiagnostic.SkippedPersistent(moduleId));
                continue;
            }

            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            CompletableFuture<Void> future = internalHandle.doUnload(this, sink)
                    .thenAccept(result -> {
                        switch (result) {
                            case LeakDetectorResult.Disabled _ -> sink.accept(new ModuleUnloadDiagnostic.LeakCheckDisabled(moduleId));
                            case LeakDetectorResult.Freed _ -> sink.accept(new ModuleUnloadDiagnostic.Freed(moduleId));
                            case LeakDetectorResult.Leaked leaked -> sink.accept(new ModuleUnloadDiagnostic.Leaked(moduleId, leaked.cause()));
                        }
                    });
            futures.add(future);

            unloaded++;

        }

        if (unloaded > 0) {
            sink.accept(new ModuleUnloadDiagnostic.UnloadedN(unloaded));

        } else {
            sink.accept(new ModuleUnloadDiagnostic.NothingUnloaded());

        }

        int finalUnloaded = unloaded;
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .thenApply(_ -> getModuleBatchResult(total, finalUnloaded));
    }

    @Override
    public ModuleBatchCommand<CompletableFuture<ModuleBatchResult>> unload(
            String id,
            DependentModuleSelector selector
    ) throws ModuleUnloadException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toUnload = selector.selectDependents(id, this);

            return new AbstractModuleBatchCommand<>(toUnload) {

                @Override
                public CompletableFuture<ModuleBatchResult> confirm() {
                    return unloadBatch(handles);
                }

            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleUnloadException("Failed to select module group.", ex);

        }

    }

    @Override
    public CompletableFuture<ModuleBatchResult> unloadAll() {
        return unloadBatch(getHandles().reversed());
    }

    private ModuleBatchResult restartBatch(List<ModuleHandle> toRestart) {
        stopBatch(toRestart);
        return startBatch(toRestart.reversed());
    }

    @Override
    public ModuleBatchCommand<ModuleBatchResult> restart(String id, DependentModuleSelector selector)
            throws ModuleRestartException
    {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toRestart = selector.selectDependents(id, this);

            return new AbstractModuleBatchCommand<>(toRestart) {
                @Override
                public ModuleBatchResult confirm() {
                    return restartBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleRestartException("Failed to select module group.", ex);

        }


    }

    @Override
    public ModuleBatchResult restartAll() {
        return restartBatch(getHandles().reversed());
    }

}
