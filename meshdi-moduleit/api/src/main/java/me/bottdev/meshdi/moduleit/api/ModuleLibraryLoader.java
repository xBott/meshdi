package me.bottdev.meshdi.moduleit.api;

import lombok.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/// Loads all libraries required by a module.
public interface ModuleLibraryLoader {

    CompletableFuture<ModuleLibrariesResult> loadAll(
            @NonNull List<ModuleHandle> handles
    );

}
