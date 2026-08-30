package me.bottdev.meshdi.moduleit.core.library;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;
import me.bottdev.meshdi.moduleit.api.library.MavenRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class LocalMavenCache implements MavenRepository {

    private final Path cacheRoot;

    public LocalMavenCache(
            @NonNull Path cacheRoot
    ) {
        this.cacheRoot = cacheRoot;
    }

    @Override
    public String id() {
        return "local-cache";
    }

    @Override
    public Optional<Path> fetchPom(MavenCoordinate coordinate) {
        return fetch(coordinate, "pom");
    }

    @Override
    public Optional<Path> fetchArtifact(MavenCoordinate coordinate) {
        return fetch(coordinate, "jar");
    }

    private Optional<Path> fetch(MavenCoordinate coordinate, String extension) {
        Path target = cacheRoot.resolve(coordinate.repositoryPath(extension));
        return Files.exists(target) ? Optional.of(target) : Optional.empty();
    }

    public Path root() {
        return cacheRoot;
    }

}
