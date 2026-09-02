package me.bottdev.meshdi.moduleit.api.library;

import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.exceptions.library.LibraryFetchException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MavenBatchDownloader {

    public CompletableFuture<List<MavenRepositoryChain.FetchedFrom<Path>>> download(
            List<ResolvedMavenDependency> resolvedDependencies,
            MavenResolutionContext context
    ) {

        MavenRepositoryChain repositoryChain = context.repositoryChain();

        List<CompletableFuture<Optional<MavenRepositoryChain.FetchedFrom<Path>>>> futures = new ArrayList<>();

        for (ResolvedMavenDependency resolvedDependency : resolvedDependencies) {

            MavenCoordinate coordinate = resolvedDependency.coordinate();
            CompletableFuture<Optional<MavenRepositoryChain.FetchedFrom<Path>>> future =
                    repositoryChain.fetchArtifactAsync(coordinate)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            LibraryFetchException fetchEx = (LibraryFetchException) ex;
                            context.diagnosticsBuilder().append(
                                    new LibraryLoadDiagnostic.DownloadFailed(coordinate, fetchEx.getId(), ex));

                        } else if (result.isPresent()) {
                            context.diagnosticsBuilder().append(
                                    new LibraryLoadDiagnostic.DownloadCompleted(coordinate, result.get().repositoryId()));

                        } else {
                            context.diagnosticsBuilder().append(
                                    new LibraryLoadDiagnostic.DownloadFailed(coordinate, "all", new Throwable("Artifact not found in any repository")));

                        }
                    });

            futures.add(future);

        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(_ -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList())
                );

    }

}
