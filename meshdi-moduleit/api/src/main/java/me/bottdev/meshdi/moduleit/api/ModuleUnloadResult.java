package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleUnloadDiagnostic;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/// A result of [ModuleManager#unload(java.lang.String, me.bottdev.meshdi.moduleit.api.ModuleSelectionStrategy)] method which returns
/// Diagnostics of unloading process and [CompletableFuture] that allows you to handle GC report.
public interface ModuleUnloadResult {

    /// @return Diagnostics of unloading process.
    Diagnostics<ModuleUnloadDiagnostic> diagnostics();

    /// @return Completable future to handle gc reports.
    CompletableFuture<List<ModuleUnloadGCReport>> gc();

}
