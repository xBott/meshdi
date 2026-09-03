package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.kern.commons.diagnostic.DiagnosticsBuilder;
import me.bottdev.kern.commons.diagnostic.ListDiagnostics;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleLibrariesResult;
import me.bottdev.meshdi.moduleit.api.ModuleLibraryLoader;
import me.bottdev.meshdi.moduleit.api.ModuleLoadEnvironment;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.library.*;
import me.bottdev.meshdi.moduleit.api.library.repositories.MavenRepositoryFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ParallelModuleLibraryLoader implements ModuleLibraryLoader {

    private final ModuleLoadEnvironment environment;
    private final MavenRepositoryChain globalRepositoryChain;
    private final MavenDependencyResolver dependencyResolver;
    private final MavenBatchDownloader downloader;
    private final MavenRepositoryFactory repositoryFactory;

    private final Map<String, PomModel> sharedPomCache = new ConcurrentHashMap<>();

    public ParallelModuleLibraryLoader(
            @NonNull ModuleLoadEnvironment environment,
            @NonNull MavenRepositoryChain globalRepositoryChain,
            @NonNull MavenDependencyResolver dependencyResolver,
            @NonNull MavenBatchDownloader downloader,
            @NonNull MavenRepositoryFactory repositoryFactory
    ) {
        this.environment = environment;
        this.globalRepositoryChain = globalRepositoryChain;
        this.dependencyResolver = dependencyResolver;
        this.downloader = downloader;
        this.repositoryFactory = repositoryFactory;
    }

    private Set<LibraryRequirement> collectSharedRequirements(List<ModuleHandle> handles) {
        return handles.stream()
                .flatMap(handle -> handle.descriptor().libraries().stream())
                .filter(library -> library.scope() == LibraryScope.SHARED)
                .collect(Collectors.toSet());
    }

    private List<MavenRepository> createRepositories(ModuleHandle handle) {
        return handle.descriptor().repositories().stream()
                .map(declaration -> repositoryFactory.create(declaration.id(), declaration.url()))
                .toList();
    }

    private MavenRepositoryChain createSharedRepositoryChain(List<ModuleHandle> handles) {
        List<MavenRepository> customRepositories = handles.stream()
                .flatMap(handle -> createRepositories(handle).stream())
                .toList();

        return globalRepositoryChain.withRepositories(customRepositories);
    }

    private MavenRepositoryChain createIsolatedRepositoryChain(ModuleHandle handle) {
        return globalRepositoryChain.withRepositories(createRepositories(handle));
    }

    private CompletableFuture<Map.Entry<ModuleHandle, List<Path>>> resolveAndDownloadIsolatedAsync(
            ModuleHandle handle,
            Set<String> exclusions,
            DiagnosticSink<LibraryLoadDiagnostic> sink
    ) {
        Set<LibraryRequirement> requirements = handle.descriptor().libraries().stream()
                .filter(requirement -> requirement.scope() == LibraryScope.ISOLATED)
                .collect(Collectors.toSet());

        if (requirements.isEmpty()) {
            return CompletableFuture.completedFuture(Map.entry(handle, List.of()));
        }

        MavenRepositoryChain isolatedRepositoryChain = createIsolatedRepositoryChain(handle);
        MavenResolutionContext context = new MavenResolutionContext(isolatedRepositoryChain, sharedPomCache, sink);

        return CompletableFuture.supplyAsync(() -> dependencyResolver.resolve(requirements, exclusions, context))
                .thenCompose(resolved -> downloader.download(resolved, context))
                .thenApply(fetched -> {

                    List<Path> paths = fetched.stream()
                            .map(MavenRepositoryChain.FetchedFrom::value)
                            .toList();

                    return Map.entry(handle, paths);

                });
    }

    private record SharedData(Set<String> exclusions, List<Path> paths) {}

    @Override
    public CompletableFuture<ModuleLibrariesResult> loadAll(@NonNull List<ModuleHandle> handles) {

        DiagnosticsBuilder<LibraryLoadDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();
        DiagnosticSink<LibraryLoadDiagnostic> sink = environment.<LibraryLoadDiagnostic>createDiagnosticSink()
                .andThen(diagnosticsBuilder::append);

        Set<LibraryRequirement> shared = collectSharedRequirements(handles);
        MavenResolutionContext sharedContext = new MavenResolutionContext(createSharedRepositoryChain(handles), sharedPomCache, sink);

        return CompletableFuture.supplyAsync(() -> dependencyResolver.resolve(shared, Set.of(), sharedContext))
                .thenCompose(sharedResolved -> {
                    Set<String> isolatedExclusions = sharedResolved.stream()
                            .map(dependency -> dependency.coordinate().moduleKey())
                            .collect(Collectors.toSet());

                    return downloader.download(sharedResolved, sharedContext)
                            .thenApply(sharedFetched -> {
                                List<Path> sharedPaths = sharedFetched.stream()
                                        .map(MavenRepositoryChain.FetchedFrom::value)
                                        .toList();
                                return new SharedData(isolatedExclusions, sharedPaths);
                            });
                })
                .thenCompose(sharedData -> {
                    List<CompletableFuture<Map.Entry<ModuleHandle, List<Path>>>> isolatedFutures = handles.stream()
                            .map(handle -> resolveAndDownloadIsolatedAsync(handle, sharedData.exclusions(), sink))
                            .toList();

                    return CompletableFuture.allOf(isolatedFutures.toArray(CompletableFuture[]::new))
                            .thenApply(_ -> {
                                Map<ModuleHandle, List<Path>> isolatedMap = isolatedFutures.stream()
                                        .map(CompletableFuture::join)
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                                return new ModuleLibrariesResult(sharedData.paths(), isolatedMap, diagnosticsBuilder.has(DiagnosticSeverity.ERROR));
                            });
                });
    }

}
