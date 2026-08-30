package me.bottdev.meshdi.moduleit.api.library;

import me.bottdev.meshdi.moduleit.api.exceptions.library.LibraryFetchException;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface MavenRepository {

    String id();

    Optional<Path> fetchPom(MavenCoordinate coordinate) throws LibraryFetchException;

    Optional<Path> fetchArtifact(MavenCoordinate coordinate) throws LibraryFetchException;

    CompletableFuture<Optional<Path>> fetchArtifactAsync(MavenCoordinate coordinate);

}
