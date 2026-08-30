package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the complete result of loading libraries for a set of modules.
 * This structure provides the necessary filesystem paths to build Module ClassLoaders.
 * 
 * @param sharedLibraries   The list of JAR paths that belong to the shared scope.
 * @param isolatedLibraries A map linking each ModuleHandle to its isolated JAR paths.
 * @param diagnostics       The aggregated diagnostic events (including downloads and conflicts).
 */
public record ModuleLibrariesResult(
        List<Path> sharedLibraries,
        Map<ModuleHandle, List<Path>> isolatedLibraries,
        Diagnostics<LibraryLoadDiagnostic> diagnostics
) {}
