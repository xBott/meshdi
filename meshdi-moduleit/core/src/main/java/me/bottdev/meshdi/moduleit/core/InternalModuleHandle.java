package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.diagnostic.DiagnosticsBuilder;
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

    boolean doLoad(SimpleModuleManager manager, DiagnosticsBuilder<ModuleLoadDiagnostic> builder);

    boolean doStart(SimpleModuleManager manager, DiagnosticsBuilder<ModuleStartDiagnostic> builder);

    boolean doStop(SimpleModuleManager manager, DiagnosticsBuilder<ModuleStopDiagnostic> builder);

    CompletableFuture<LeakDetectorResult> doUnload(SimpleModuleManager manager, DiagnosticsBuilder<ModuleUnloadDiagnostic> builder);

}
