package me.bottdev.meshdi.moduleit.api;

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
    /// @return BatchResult of resolution process.
    ModuleBatchResult resolve(ModuleRepository repository) throws CandidateListException;

    /// Tries to prepare a specified module using a concrete group selection strategy.
    /// Loads libraries from Maven Repositories.
    /// Ready modules are stored in manager with [ModuleState#READY] state.
    /// @return module prepare command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModulePrepareException if an cause occurred during module preparation.
    ModuleBatchCommand<CompletableFuture<ModuleBatchResult>> prepare(
            String id,
            DependencyModuleSelector selector
    ) throws ModulePrepareException;

    /// Prepares all modules, that are not ready yet.
    /// @return Completable Future with result of preparation process.
    CompletableFuture<ModuleBatchResult> prepareAll();

    /// Loads modules from a provided repository.
    /// @return module load command.
    ModuleBatchCommand<ModuleBatchResult> load(
            String id,
            DependencyModuleSelector selector
    ) throws ModuleLoadException;

    /// Loads all modules that are ready.
    /// @return Result of loading process.
    ModuleBatchResult loadAll();

    /// Tries to start a specified module using a concrete group selection strategy.
    /// Module must be loaded before calling stop.
    /// @return module start command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleStartException if an cause occurred during module startup.
    ModuleBatchCommand<ModuleBatchResult> start(
            String id,
            DependencyModuleSelector selector
    ) throws ModuleStartException;

    /// Starts all modules, that have not started yet.
    /// @return Result of starting process.
    ModuleBatchResult startAll();

    /// Tries to stop a specified module using a concrete group selection strategy.
    /// Modules are kept in memory, but its context is disposed.
    /// Module must be started before calling stop.
    /// @return module stop command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleStopException if an cause occurred during module unloading.
    ModuleBatchCommand<ModuleBatchResult> stop(
            String id,
            DependentModuleSelector selector
    ) throws ModuleStopException;

    /// Stops all started modules.
    /// @return Result of stopping process.
    ModuleBatchResult stopAll();

    /// Tries to unload a specified module using a concrete group selection strategy.
    /// Modules are completely unloaded from JVM.
    /// Module must be stopped or just loaded before calling unload.
    /// @return module unload command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleUnloadException if an cause occurred during module unloading.
    ModuleBatchCommand<CompletableFuture<ModuleBatchResult>> unload(
            String id,
            DependentModuleSelector selector
    ) throws ModuleUnloadException;

    /// Unloads all stopped or just loaded modules.
    /// @return Completable future with result of loading process.
    CompletableFuture<ModuleBatchResult> unloadAll();

    /// Tries to restart a specified module using a concrete group selection strategy.
    /// Module must be started before calling restart.
    /// @return module restart command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleRestartException if an cause occurred during module restart.
    ModuleBatchCommand<ModuleBatchResult> restart(
            String id,
            DependentModuleSelector selector
    ) throws ModuleRestartException;

    /// Restarts all modules.
    /// If module is not started, it will be started automatically.
    /// @return Result of restarting process.
    ModuleBatchResult restartAll();

}
