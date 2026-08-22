package me.bottdev.meshdi.moduleit.api.library;

import java.nio.file.Path;
import java.util.List;

/// The result of a successful resolution and download is the file path -
/// the actual path to the JAR file on the disk—and the SHA-256 hash for verification.
public record ResolvedArtifact(
        MavenCoordinate coordinate,
        Path jarPath,
        String sha256,
        LibraryScope scope,
        boolean optional,
        List<MavenCoordinate> excludedFrom
) {}