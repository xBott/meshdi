package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.meshdi.moduleit.api.diagnostic.*;
import me.bottdev.meshdi.moduleit.api.exceptions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/// Orchestrator of modules - **handle lifecycle of all modules**.
public interface ModuleManager {

    /// @return Load environment of the module manager.
    ModuleLoadEnvironment environment();

    /// @return Leak Detector of the module manager, which allows to track module unloading leaks.
    ModuleClassLoaderLeakDetector leakDetector();

    /// @return A list of all module handles.
    List<ModuleHandle> getHandles();

    /// @return Indicates whether the specified module is loaded.
    boolean exists(String id);

    /// @return Module handle with specified id or null.
    ModuleHandle getHandle(String id);

    /// @return A list of module handles specified module depends on.
    List<ModuleHandle> getDependencyHandles(String id);

    /// @return A list of module handles specified module depends on.
    List<ModuleHandle> getDependentHandles(String id);

    /// Resolves modules from a provided repository.
    /// Resolved modules are stored in manager with [ModuleState#RESOLVED] state.
    /// @throws CandidateListException when repository failed to list candidates.
    /// @return Diagnostics of resolution process.
    Diagnostics<ModuleResolutionDiagnostic> resolve(ModuleRepository repository) throws CandidateListException;

    /// Tries to prepare a specified module using a concrete group selection strategy.
    /// Loads libraries from Maven Repositories.
    /// Ready modules are stored in manager with [ModuleState#READY] state.
    /// @return module prepare command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModulePrepareException if an error occurred during module preparation.
    ModuleBatchCommand<CompletableFuture<Diagnostics<LibraryLoadDiagnostic>>> prepare(
            String id,
            DependencyModuleSelector selector
    ) throws ModulePrepareException;

    /// Prepares all modules, that are not ready yet.
    /// @return Completable Future with diagnostics of preparation process.
    CompletableFuture<Diagnostics<LibraryLoadDiagnostic>> prepareAll();

    /// Loads modules from a provided repository.
    /// @return module load command.
    ModuleBatchCommand<Diagnostics<ModuleLoadDiagnostic>> load(
            String id,
            DependencyModuleSelector selector
    ) throws ModuleLoadException;

    /// Loads all modules that are ready.
    /// @return Diagnostics of loading process.
    Diagnostics<ModuleLoadDiagnostic> loadAll();

    /// Tries to start a specified module using a concrete group selection strategy.
    /// Module must be loaded before calling stop.
    /// @return module start command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleStartException if an error occurred during module startup.
    ModuleBatchCommand<Diagnostics<ModuleStartDiagnostic>> start(
            String id,
            DependencyModuleSelector selector
    ) throws ModuleStartException;

    /// Starts all modules, that have not started yet.
    /// @return Diagnostics of starting process.
    Diagnostics<ModuleStartDiagnostic> startAll();

    /// Tries to stop a specified module using a concrete group selection strategy.
    /// Modules are kept in memory, but its context is disposed.
    /// Module must be started before calling stop.
    /// @return module stop command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleStopException if an error occurred during module unloading.
    ModuleBatchCommand<Diagnostics<ModuleStopDiagnostic>> stop(
            String id,
            DependentModuleSelector selector
    ) throws ModuleStopException;

    /// Stops all started modules.
    /// @return Diagnostics of stopping process.
    Diagnostics<ModuleStopDiagnostic> stopAll();

    /// Tries to unload a specified module using a concrete group selection strategy.
    /// Modules are completely unloaded from JVM.
    /// Module must be stopped or just loaded before calling unload.
    /// @return module unload command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleUnloadException if an error occurred during module unloading.
    ModuleBatchCommand<CompletableFuture<Diagnostics<ModuleUnloadDiagnostic>>> unload(
            String id,
            DependentModuleSelector selector
    ) throws ModuleUnloadException;

    /// Unloads all stopped or just loaded modules.
    /// @return Completable future with diagnostics of loading process.
    CompletableFuture<Diagnostics<ModuleUnloadDiagnostic>> unloadAll();

    /// Tries to restart a specified module using a concrete group selection strategy.
    /// Module must be started before calling restart.
    /// @return module restart command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleRestartException if an error occurred during module restart.
    ModuleBatchCommand<Diagnostics<ModuleRestartDiagnostic>> restart(
            String id,
            DependentModuleSelector selector
    ) throws ModuleRestartException;

    /// Restarts all modules.
    /// If module is not started, it will be started automatically.
    /// @return Diagnostics of restarting process.
    Diagnostics<ModuleRestartDiagnostic> restartAll();

}
