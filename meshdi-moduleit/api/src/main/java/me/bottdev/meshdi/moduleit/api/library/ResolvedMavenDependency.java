package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;

public record ResolvedMavenDependency(
        @NonNull MavenCoordinate coordinate,
        int depth,
        @NonNull String requestedBy
) {}
