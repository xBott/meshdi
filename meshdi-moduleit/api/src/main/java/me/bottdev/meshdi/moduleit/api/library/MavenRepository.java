package me.bottdev.meshdi.moduleit.api.library;

import java.nio.file.Path;
import java.util.Optional;

public interface MavenRepository {

    String id();

    Optional<Path> fetchPom(MavenCoordinate coordinate);

    Optional<Path> fetchArtifact(MavenCoordinate coordinate);

}
