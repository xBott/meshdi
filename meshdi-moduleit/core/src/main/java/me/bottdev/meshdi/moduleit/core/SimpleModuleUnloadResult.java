package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.meshdi.moduleit.api.ModuleUnloadGCReport;
import me.bottdev.meshdi.moduleit.api.ModuleUnloadResult;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleUnloadDiagnostic;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public record SimpleModuleUnloadResult(
        Diagnostics<ModuleUnloadDiagnostic> diagnostics,
        CompletableFuture<List<ModuleUnloadGCReport>> gc
) implements ModuleUnloadResult {}
