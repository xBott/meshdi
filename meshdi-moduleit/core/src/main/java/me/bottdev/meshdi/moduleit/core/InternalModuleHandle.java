package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.meshdi.moduleit.api.LeakDetectorResult;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStartDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStopDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleUnloadDiagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

interface InternalModuleHandle extends ModuleHandle {

    void completePreparation(List<Path> libraries);

    boolean doLoad(SimpleModuleManager manager, DiagnosticSink<ModuleLoadDiagnostic> sink);

    boolean doStart(SimpleModuleManager manager, DiagnosticSink<ModuleStartDiagnostic> sink);

    boolean doStop(SimpleModuleManager manager, DiagnosticSink<ModuleStopDiagnostic> sink);

    /// Unloads the module.
    /// @return an already completed future with `null` if the module could not be unloaded,
    /// otherwise a future that completes with the result of the class loader leak check.
    CompletableFuture<LeakDetectorResult> doUnload(SimpleModuleManager manager, DiagnosticSink<ModuleUnloadDiagnostic> sink);

}
