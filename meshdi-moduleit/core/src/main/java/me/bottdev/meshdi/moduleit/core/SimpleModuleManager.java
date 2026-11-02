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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProviderContainer;
import org.semver4j.Semver;
import org.semver4j.range.RangeList;

import java.net.URLClassLoader;

public class SimpleModuleManager implements ModuleManager {

    private final StatefulVersionedDependencyResolver<String, ModuleCandidate> dependencyResolver;
    private final ModuleLoadEnvironment environment;
    private final ModuleLibraryLoader libraryLoader;
    private final ContextMesh contextMesh;
    private final ModuleClassLoaderLeakDetector leakDetector;

    private static class SharedLibraryLoader extends URLClassLoader {
        public SharedLibraryLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }
        public void addSharedLibrary(URL url) {
            super.addURL(url);
        }
    }

    private final LinkedHashMap<String, InternalModuleHandle> handles = new LinkedHashMap<>();
    private final Set<Path> sharedLibraries = new HashSet<>();
    private final SharedLibraryLoader sharedLibraryLoader = new SharedLibraryLoader(new URL[0], ClassLoader.getPlatformClassLoader());

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
        for (Path path : paths) {
            if (sharedLibraries.add(path)) {
                try {
                    sharedLibraryLoader.addSharedLibrary(path.toUri().toURL());
                } catch (MalformedURLException ignored) {}
            }
        }
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

    /// Runs a synchronous transition on every handle and splits them into processed and failed.
    private static ModuleBatchResult<ModuleHandle> runBatch(
            List<ModuleHandle> toProcess,
            Predicate<InternalModuleHandle> transition
    ) {
        List<ModuleHandle> processed = new ArrayList<>();
        List<ModuleHandle> failed = new ArrayList<>();

        for (ModuleHandle handle : toProcess) {
            if (transition.test((InternalModuleHandle) handle)) {
                processed.add(handle);
            } else {
                failed.add(handle);
            }
        }

        return ModuleBatchResult.of(processed, failed);
    }

    /// Selects unique candidates that can be resolved.
    /// Rejected candidates (duplicates, API version mismatch) are added to `rejected`.
    /// Candidates of already existing modules are skipped.
    private Set<ModuleCandidate> resolveUniqueCandidates(
            List<ModuleCandidate> candidates,
            List<ModuleCandidate> rejected,
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
                rejected.add(candidate);
                continue;
            }

            if (!environment.apiVersion().satisfies(requiredApiVersion)) {
                sink.accept(new ModuleResolutionDiagnostic.ApiVersionMismatch(
                        moduleId,
                        requiredApiVersion,
                        environment.apiVersion())
                );
                rejected.add(candidate);
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
    public ModuleBatchResult<ModuleCandidate> resolve(ModuleRepository repository) throws CandidateListException {

        DiagnosticSink<ModuleResolutionDiagnostic> sink = environment.createDiagnosticSink();

        List<ModuleCandidate> candidates = repository.candidates();
        if (candidates.isEmpty()) {
            sink.accept(new ModuleResolutionDiagnostic.NothingResolved());
            return new ModuleBatchResult.Unchanged<>();
        }

        List<ModuleCandidate> rejected = new ArrayList<>();
        Collection<ModuleCandidate> uniqueCandidates = resolveUniqueCandidates(candidates, rejected, sink);
        if (uniqueCandidates.isEmpty()) {
            sink.accept(new ModuleResolutionDiagnostic.NothingResolved());
            return ModuleBatchResult.of(List.of(), rejected);
        }

        Optional<ResolutionResult<String, ModuleCandidate>> diagnosticResult = resolveDependencies(uniqueCandidates, sink);
        if (diagnosticResult.isEmpty()) {
            sink.accept(new ModuleResolutionDiagnostic.NothingResolved());
            rejected.addAll(uniqueCandidates);
            return new ModuleBatchResult.Failed<>(rejected);
        }

        ResolutionResult<String, ModuleCandidate> resolutionResult = diagnosticResult.get();
        List<ModuleCandidate> ordered = resolutionResult.ordered();
        int processedCount = ordered.size();

        // Unique candidates that the dependency resolver did not include are rejected as well.
        Set<ModuleCandidate> resolvedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        resolvedSet.addAll(ordered);
        for (ModuleCandidate candidate : uniqueCandidates) {
            if (!resolvedSet.contains(candidate)) rejected.add(candidate);
        }

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

        return ModuleBatchResult.of(ordered, rejected);
    }

    private CompletableFuture<ModuleBatchResult<ModuleHandle>> prepareBatch(List<ModuleHandle> toPrepare) {

        List<ModuleHandle> resolved = toPrepare.stream()
                .filter(handle -> handle.state() == ModuleState.RESOLVED)
                .toList();

        if (resolved.isEmpty()) return CompletableFuture.completedFuture(new ModuleBatchResult.Unchanged<>());

        DiagnosticSink<LibraryLoadDiagnostic> sink = environment.createDiagnosticSink();

        return libraryLoader.loadAll(resolved, sink).thenApply(result -> {

            addSharedLibraries(result.sharedLibraries());

            List<ModuleHandle> prepared = new ArrayList<>();
            List<ModuleHandle> failed = new ArrayList<>();

            for (ModuleHandle handle : resolved) {
                if (result.failedModules().contains(handle)) {
                    failed.add(handle);
                    continue;
                }
                InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
                List<Path> libraries = result.isolatedLibraries().getOrDefault(handle, List.of());
                internalHandle.completePreparation(libraries);
                prepared.add(handle);
            }

            return ModuleBatchResult.of(prepared, failed);

        });

    }

    @Override
    public ModuleBatchCommand<CompletableFuture<ModuleBatchResult<ModuleHandle>>> prepare(
            String id,
            DependencyModuleSelector selector
    ) throws ModulePrepareException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {
            List<ModuleHandle> toPrepare = selector.selectDependencies(id, this);

            return new AbstractModuleBatchCommand<>(toPrepare) {
                @Override
                public CompletableFuture<ModuleBatchResult<ModuleHandle>> confirm() {
                    return prepareBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModulePrepareException("Failed to select module group.", ex);

        }

    }

    @Override
    public CompletableFuture<ModuleBatchResult<ModuleHandle>> prepareAll() {
        return prepareBatch(getHandles());
    }

    ModuleClassLoader createClassLoader(ModuleHandle handle) throws MalformedURLException {
        ModuleDescriptor descriptor = handle.descriptor();
        String moduleId = descriptor.id();

        List<URL> allUrls = new ArrayList<>();
        allUrls.add(handle.candidate().sourceUrl());
        for (Path libPath : handle.libraries()) {
            allUrls.add(libPath.toUri().toURL());
        }

        List<ClassProvider> providers = new ArrayList<>(environment.createBaseProviders(moduleId, sharedLibraryLoader));

        List<String> dependencyIds = descriptor.getVersionedDependencies().stream()
                .map(VersionedDependencyRequest::key)
                .toList();

        return new ModuleClassLoader(
                moduleId,
                allUrls.toArray(new URL[0]),
                new ClassProviderContainer(providers),
                dependencyIds
        );
    }


    private ModuleBatchResult<ModuleHandle> loadBatch(List<ModuleHandle> toLoad) {

        if (toLoad.isEmpty()) return new ModuleBatchResult.Unchanged<>();

        DiagnosticSink<ModuleLoadDiagnostic> sink = environment.createDiagnosticSink();
        ModuleBatchResult<ModuleHandle> result = runBatch(toLoad, handle -> handle.doLoad(this, sink));

        int loaded = result.processed().size();
        if (loaded > 0) {
            sink.accept(new ModuleLoadDiagnostic.LoadedN(loaded));
        } else {
            sink.accept(new ModuleLoadDiagnostic.NothingLoaded());
        }

        return result;
    }

    @Override
    public ModuleBatchCommand<ModuleBatchResult<ModuleHandle>> load(String id, DependencyModuleSelector selector) throws ModuleLoadException {
        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {
            List<ModuleHandle> toLoad = selector.selectDependencies(id, this);

            return new AbstractModuleBatchCommand<>(toLoad) {
                @Override
                public ModuleBatchResult<ModuleHandle> confirm() {
                    return loadBatch(toLoad);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleLoadException("Failed to select module group.", ex);
        }
    }

    @Override
    public ModuleBatchResult<ModuleHandle> loadAll() {
        return loadBatch(getHandles());
    }


    private ModuleBatchResult<ModuleHandle> startBatch(List<ModuleHandle> toStart) {

        if (toStart.isEmpty()) return new ModuleBatchResult.Unchanged<>();

        DiagnosticSink<ModuleStartDiagnostic> sink = environment.createDiagnosticSink();
        ModuleBatchResult<ModuleHandle> result = runBatch(toStart, handle -> handle.doStart(this, sink));

        int started = result.processed().size();
        if (started > 0) {
            sink.accept(new ModuleStartDiagnostic.StartedN(started));

        } else {
            sink.accept(new ModuleStartDiagnostic.NothingStarted());

        }

        return result;

    }

    @Override
    public ModuleBatchCommand<ModuleBatchResult<ModuleHandle>> start(String id, DependencyModuleSelector selector) throws
            ModuleStartException
    {
        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStart = selector.selectDependencies(id, this);

            return new AbstractModuleBatchCommand<>(toStart) {
                @Override
                public ModuleBatchResult<ModuleHandle> confirm() {
                    return startBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleStartException("Failed to select module group.", ex);

        }

    }

    @Override
    public ModuleBatchResult<ModuleHandle> startAll() {
        return startBatch(getHandles());
    }


    private ModuleBatchResult<ModuleHandle> stopBatch(List<ModuleHandle> toStop) {

        if (toStop.isEmpty()) return new ModuleBatchResult.Unchanged<>();

        DiagnosticSink<ModuleStopDiagnostic> sink = environment.createDiagnosticSink();
        ModuleBatchResult<ModuleHandle> result = runBatch(toStop, handle -> handle.doStop(this, sink));

        int stopped = result.processed().size();
        if (stopped > 0) {
            sink.accept(new ModuleStopDiagnostic.StoppedN(stopped));

        } else {
            sink.accept(new ModuleStopDiagnostic.NothingStopped());

        }

        return result;

    }

    @Override
    public ModuleBatchCommand<ModuleBatchResult<ModuleHandle>> stop(String id, DependentModuleSelector selector)
            throws ModuleStopException
    {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toStop = selector.selectDependents(id, this);

            return new AbstractModuleBatchCommand<>(toStop) {
                @Override
                public ModuleBatchResult<ModuleHandle> confirm() {
                    return stopBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleStopException("Failed to select module group.", ex);

        }

    }

    @Override
    public ModuleBatchResult<ModuleHandle> stopAll() {
        return stopBatch(getHandles().reversed());
    }


    private CompletableFuture<ModuleBatchResult<ModuleHandle>> unloadBatch(List<ModuleHandle> toUnload) {

        if (toUnload.isEmpty()) return CompletableFuture.completedFuture(new ModuleBatchResult.Unchanged<>());

        DiagnosticSink<ModuleUnloadDiagnostic> sink = environment.createDiagnosticSink();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Persistent modules are skipped: they are neither processed nor failed.
        List<ModuleHandle> unloaded = new ArrayList<>();
        List<ModuleHandle> failed = new ArrayList<>();

        for (ModuleHandle handle : toUnload) {
            String moduleId = handle.descriptor().id();

            if (handle.isPersistent()) {
                sink.accept(new ModuleUnloadDiagnostic.SkippedPersistent(moduleId));
                continue;
            }

            InternalModuleHandle internalHandle = (InternalModuleHandle) handle;
            CompletableFuture<LeakDetectorResult> unloadFuture = internalHandle.doUnload(this, sink);

            // doUnload completes synchronously with null when the module could not be unloaded,
            // otherwise the future tracks only the asynchronous leak check.
            if (unloadFuture.isDone() && unloadFuture.getNow(null) == null) {
                failed.add(handle);
                continue;
            }

            unloaded.add(handle);
            futures.add(unloadFuture.thenAccept(result -> {
                switch (result) {
                    case LeakDetectorResult.Disabled _ -> sink.accept(new ModuleUnloadDiagnostic.LeakCheckDisabled(moduleId));
                    case LeakDetectorResult.Freed _ -> sink.accept(new ModuleUnloadDiagnostic.Freed(moduleId));
                    case LeakDetectorResult.Leaked leaked -> sink.accept(new ModuleUnloadDiagnostic.Leaked(moduleId, leaked.cause()));
                }
            }));

        }

        if (!unloaded.isEmpty()) {
            sink.accept(new ModuleUnloadDiagnostic.UnloadedN(unloaded.size()));

        } else {
            sink.accept(new ModuleUnloadDiagnostic.NothingUnloaded());

        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .thenApply(_ -> ModuleBatchResult.of(unloaded, failed));
    }

    @Override
    public ModuleBatchCommand<CompletableFuture<ModuleBatchResult<ModuleHandle>>> unload(
            String id,
            DependentModuleSelector selector
    ) throws ModuleUnloadException {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toUnload = selector.selectDependents(id, this);

            return new AbstractModuleBatchCommand<>(toUnload) {

                @Override
                public CompletableFuture<ModuleBatchResult<ModuleHandle>> confirm() {
                    return unloadBatch(handles);
                }

            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleUnloadException("Failed to select module group.", ex);

        }

    }

    @Override
    public CompletableFuture<ModuleBatchResult<ModuleHandle>> unloadAll() {
        return unloadBatch(getHandles().reversed());
    }

    /// Result reflects the start phase: non-started modules are started as well.
    private ModuleBatchResult<ModuleHandle> restartBatch(List<ModuleHandle> toRestart) {
        if (toRestart.isEmpty()) return new ModuleBatchResult.Unchanged<>();
        stopBatch(toRestart);
        return startBatch(toRestart.reversed());
    }

    @Override
    public ModuleBatchCommand<ModuleBatchResult<ModuleHandle>> restart(String id, DependentModuleSelector selector)
            throws ModuleRestartException
    {

        if (!exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        try {

            List<ModuleHandle> toRestart = selector.selectDependents(id, this);

            return new AbstractModuleBatchCommand<>(toRestart) {
                @Override
                public ModuleBatchResult<ModuleHandle> confirm() {
                    return restartBatch(handles);
                }
            };

        } catch (ModuleSelectionException ex) {
            throw new ModuleRestartException("Failed to select module group.", ex);

        }


    }

    @Override
    public ModuleBatchResult<ModuleHandle> restartAll() {
        return restartBatch(getHandles().reversed());
    }

}
