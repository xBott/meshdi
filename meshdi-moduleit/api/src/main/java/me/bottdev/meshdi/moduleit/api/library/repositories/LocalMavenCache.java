package me.bottdev.meshdi.moduleit.api.library.repositories;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;
import me.bottdev.meshdi.moduleit.api.library.MavenRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class LocalMavenCache implements MavenRepository {

    private final String id;
    private final Path cacheRoot;

    public LocalMavenCache(
            @NonNull String id,
            @NonNull Path cacheRoot
    ) {
        this.id = id;
        this.cacheRoot = cacheRoot;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Optional<Path> fetchPom(MavenCoordinate coordinate) {
        return fetch(coordinate, "pom");
    }

    @Override
    public Optional<Path> fetchArtifact(MavenCoordinate coordinate) {
        return fetch(coordinate, "jar");
    }

    @Override
    public CompletableFuture<Optional<Path>> fetchArtifactAsync(MavenCoordinate coordinate) {
        return CompletableFuture.completedFuture(fetchArtifact(coordinate));
    }

    private Optional<Path> fetch(MavenCoordinate coordinate, String extension) {
        Path target = cacheRoot.resolve(coordinate.repositoryPath(extension));
        return Files.exists(target) ? Optional.of(target) : Optional.empty();
    }

    public Path root() {
        return cacheRoot;
    }

}
