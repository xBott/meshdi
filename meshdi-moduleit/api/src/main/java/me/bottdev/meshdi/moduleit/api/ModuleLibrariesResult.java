package me.bottdev.meshdi.moduleit.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Result of loading libraries for a batch of modules: the jar paths needed to build module class loaders.
///
/// @param sharedLibraries   jars of the shared scope, loaded by one class loader common to all modules.
/// @param isolatedLibraries jars of the isolated scope of each module, loaded by the module's own class loader.
/// @param failedModules     modules whose libraries could not be fully resolved or downloaded.
///                          Libraries of other modules are unaffected.
public record ModuleLibrariesResult(
        List<Path> sharedLibraries,
        Map<ModuleHandle, List<Path>> isolatedLibraries,
        Set<ModuleHandle> failedModules
) {

    public ModuleLibrariesResult {
        sharedLibraries = List.copyOf(sharedLibraries);
        isolatedLibraries = Map.copyOf(isolatedLibraries);
        failedModules = Set.copyOf(failedModules);
    }

    /// @return true if libraries of at least one module failed.
    public boolean failed() {
        return !failedModules.isEmpty();
    }

}
