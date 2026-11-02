package me.bottdev.meshdi.moduleit.api;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/// Resolves and downloads libraries required by modules.
///
/// A [ModuleManager] uses one loader for its whole lifetime. Shared libraries are added to a single
/// class loader that only grows, so a loader must keep versions of shared libraries consistent
/// across calls: a library returned as shared once must not be returned in another version later.
public interface ModuleLibraryLoader {

    /// Resolves libraries of the modules, including their transitive dependencies.
    /// @return libraries to load. Modules whose libraries failed are listed in
    /// [ModuleLibrariesResult#failedModules()]; libraries of other modules are still returned.
    CompletableFuture<ModuleLibrariesResult> loadAll(
            @NonNull List<ModuleHandle> handles,
            @NonNull DiagnosticSink<LibraryLoadDiagnostic> sink
    );

}
