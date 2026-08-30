package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;

public record RepositoryDeclaration(
        @NonNull String id,
        @NonNull String url
) {}
