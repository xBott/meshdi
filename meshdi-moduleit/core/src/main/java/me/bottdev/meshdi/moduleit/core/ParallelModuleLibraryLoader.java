package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticsBuilder;
import me.bottdev.kern.commons.diagnostic.ListDiagnostics;
import me.bottdev.kern.commons.download.DownloadManager;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleLibrariesResult;
import me.bottdev.meshdi.moduleit.api.ModuleLibraryLoader;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.library.*;
import me.bottdev.meshdi.moduleit.api.library.repositories.LocalMavenCache;
import me.bottdev.meshdi.moduleit.api.library.repositories.RemoteMavenRepository;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ParallelModuleLibraryLoader implements ModuleLibraryLoader {

    private final MavenRepositoryChain globalRepositoryChain;
    private final MavenDependencyResolver dependencyResolver;
    private final MavenBatchDownloader downloader;

    private final HttpClient httpClient;
    private final LocalMavenCache localCache;
    private final DownloadManager downloadManager;

    private final Map<String, PomModel> sharedPomCache = new ConcurrentHashMap<>();

    public ParallelModuleLibraryLoader(
            @NonNull MavenRepositoryChain globalRepositoryChain,
            @NonNull MavenDependencyResolver dependencyResolver,
            @NonNull MavenBatchDownloader downloader,
            @NonNull HttpClient httpClient,
            @NonNull LocalMavenCache localCache,
            @NonNull DownloadManager downloadManager
    ) {
        this.globalRepositoryChain = globalRepositoryChain;
        this.dependencyResolver = dependencyResolver;
        this.downloader = downloader;

        this.httpClient = httpClient;
        this.localCache = localCache;
        this.downloadManager = downloadManager;
    }

    private Set<LibraryRequirement> collectSharedRequirements(List<ModuleHandle> handles) {
        Set<LibraryRequirement> sharedRequirements = new HashSet<>();
        for (ModuleHandle handle : handles) {
            for (LibraryRequirement library : handle.descriptor().libraries()) {
                if (library.scope() != LibraryScope.SHARED) continue;
                sharedRequirements.add(library);
            }
        }
        return sharedRequirements;
    }

    private List<MavenRepository> createRepositories(ModuleHandle handle) {
        return handle.descriptor().repositories().stream()
                .map(declaration -> new RemoteMavenRepository(
                        declaration.id(),
                        declaration.url(),
                        httpClient,
                        localCache,
                        downloadManager
                ))
                .collect(Collectors.toList());
    }

    private MavenRepositoryChain createSharedRepositoryChain(List<ModuleHandle> handles) {

        List<MavenRepository> customRepositories = new ArrayList<>();

        for (ModuleHandle handle : handles) {
            List<MavenRepository> repositories = createRepositories(handle);
            customRepositories.addAll(repositories);

        }

        return globalRepositoryChain.withRepositories(customRepositories);

    }

    private MavenRepositoryChain createIsolatedRepositoryChain(ModuleHandle handle) {
        List<MavenRepository> repositories = createRepositories(handle);
        return globalRepositoryChain.withRepositories(repositories);

    }

    private CompletableFuture<Map.Entry<ModuleHandle, List<Path>>> resolveAndDownloadIsolatedAsync(
            ModuleHandle handle,
            Set<String> exclusions,
            DiagnosticsBuilder<LibraryLoadDiagnostic> diagnosticsBuilder
    ) {
        Set<LibraryRequirement> requirements = handle.descriptor().libraries().stream()
                .filter(requirement -> requirement.scope() == LibraryScope.ISOLATED)
                .collect(Collectors.toSet());

        if (requirements.isEmpty()) {
            return CompletableFuture.completedFuture(Map.entry(handle, List.of()));
        }

        MavenRepositoryChain isolatedRepositoryChain = createIsolatedRepositoryChain(handle);
        MavenResolutionContext context = new MavenResolutionContext(isolatedRepositoryChain, sharedPomCache, diagnosticsBuilder);

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
        MavenRepositoryChain sharedRepositoryChain = createSharedRepositoryChain(handles);
        Set<LibraryRequirement> shared = collectSharedRequirements(handles);

        MavenResolutionContext sharedContext = new MavenResolutionContext(sharedRepositoryChain, sharedPomCache, diagnosticsBuilder);

        // 1. Resolve and download Shared dependencies
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
                    Set<String> isolatedExclusions = sharedData.exclusions();
                    List<Path> sharedPaths = sharedData.paths();

                    List<CompletableFuture<Map.Entry<ModuleHandle, List<Path>>>> isolatedFutures = handles.stream()
                            .map(handle -> resolveAndDownloadIsolatedAsync(handle, isolatedExclusions, diagnosticsBuilder))
                            .toList();

                    return CompletableFuture.allOf(isolatedFutures.toArray(CompletableFuture[]::new))
                            .thenApply(_ -> {
                                Map<ModuleHandle, List<Path>> isolatedMap = isolatedFutures.stream()
                                        .map(CompletableFuture::join)
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                                return new ModuleLibrariesResult(sharedPaths, isolatedMap, diagnosticsBuilder.build());
                            });
                });
    }

}
