package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.exceptions.library.LibraryFetchException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MavenRepositoryChain {

    public record FetchedFrom<T>(
            @NonNull T value,
            @NonNull String repositoryId
    ) {}

    private final List<MavenRepository> repositories;

    public MavenRepositoryChain(@NonNull List<MavenRepository> repositories) {
        this.repositories = List.copyOf(repositories);
    }

    public MavenRepositoryChain withRepository(@NonNull MavenRepository repository) {
        List<MavenRepository> newRepos = new ArrayList<>(this.repositories);
        newRepos.add(repository);
        return new MavenRepositoryChain(newRepos);
    }

    public MavenRepositoryChain withRepositories(@NonNull List<MavenRepository> additionalRepositories) {
        List<MavenRepository> newRepos = new ArrayList<>(this.repositories);
        newRepos.addAll(additionalRepositories);
        return new MavenRepositoryChain(newRepos);
    }

    public Optional<FetchedFrom<Path>> fetchPom(MavenCoordinate coordinate) throws LibraryFetchException {
        for (MavenRepository repository : repositories) {

            Optional<Path> pathOptional = repository.fetchPom(coordinate);
            if (pathOptional.isPresent()) {
                Path path = pathOptional.get();
                FetchedFrom<Path> fetchedFrom = new FetchedFrom<>(path, repository.id());
                return Optional.of(fetchedFrom);
            }

        }

        return Optional.empty();
    }

    public Optional<FetchedFrom<Path>> fetchArtifact(MavenCoordinate coordinate) throws LibraryFetchException {
        for (MavenRepository repository : repositories) {

            Optional<Path> pathOptional = repository.fetchArtifact(coordinate);
            if (pathOptional.isPresent()) {
                Path path = pathOptional.get();
                FetchedFrom<Path> fetchedFrom = new FetchedFrom<>(path, repository.id());
                return Optional.of(fetchedFrom);
            }

        }

        return Optional.empty();
    }

    public CompletableFuture<Optional<FetchedFrom<Path>>> fetchArtifactAsync(MavenCoordinate coordinate) {
        return fetchArtifactAsyncRecursive(coordinate, 0);
    }

    private CompletableFuture<Optional<FetchedFrom<Path>>> fetchArtifactAsyncRecursive(MavenCoordinate coordinate, int index) {
        if (index >= repositories.size()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        MavenRepository repository = repositories.get(index);
        return repository.fetchArtifactAsync(coordinate).thenCompose(pathOptional -> {
            if (pathOptional.isPresent()) {
                FetchedFrom<Path> fetchedFrom = new FetchedFrom<>(pathOptional.get(), repository.id());
                return CompletableFuture.completedFuture(Optional.of(fetchedFrom));
            } else {
                return fetchArtifactAsyncRecursive(coordinate, index + 1);
            }
        });
    }

}
