package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;

/// The identifier for a specific version of a specific library.
public record MavenCoordinate(
        @NonNull String groupId,
        @NonNull String artifactId,
        @NonNull String version,
        String classifier,
        @NonNull String packaging
) {

    /// Creates a maven coordinate from a string.
    /// **Example:**
    /// ```
    /// com.google.code.gson:gson:2.10.1
    /// ```
    /// **With classifier:**
    /// ```
    /// group:artifact:version:classifier
    /// ```
    public static MavenCoordinate of(String gav) {
        String[] parts = gav.split(":");
        return switch (parts.length) {
            case 3 -> new MavenCoordinate(parts[0], parts[1], parts[2], null, "jar");
            case 4 -> new MavenCoordinate(parts[0], parts[1], parts[2], parts[3], "jar");
            default -> throw new IllegalArgumentException("Invalid GAV coordinate: " + gav);
        };
    }

    /// Used for conflict resolution.
    /// @return coordinate string without a version.
    public String moduleKey() {
        return groupId + ":" + artifactId;
    }

    /// Relative path inside Maven-repository, m2-layout
    public String repositoryPath(String extension) {
        String base = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" +
                artifactId + "-" + version + (classifier != null ? "-" + classifier : "");
        return base + "." + extension;
    }

    /// Relative path for a resolved snapshot (e.g. timestamped filename)
    public String snapshotRepositoryPath(String extension, String resolvedSnapshotVersion) {
        String base = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" +
                artifactId + "-" + resolvedSnapshotVersion + (classifier != null ? "-" + classifier : "");
        return base + "." + extension;
    }

    /// Relative path to maven-metadata.xml
    public String metadataPath() {
        return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/maven-metadata.xml";
    }

    @NonNull
    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version + (classifier != null ? ":" + classifier : "");
    }

}
