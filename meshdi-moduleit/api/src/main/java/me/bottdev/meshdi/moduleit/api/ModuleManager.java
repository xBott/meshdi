package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStartDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStopDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleUnloadDiagnostic;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleStartException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleStopException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleUnloadException;

import java.util.List;

/// Orchestrator of modules - **handle lifecycle of all modules**.
public interface ModuleManager {

    ModuleLoadEnvironment environment();

    ModuleClassLoaderLeakDetector leakDetector();

    List<ModuleHandle> getHandles();

    boolean exists(String id);

    ModuleHandle getHandle(String id);

    /// @return A list of module handles specified module depends on.
    List<ModuleHandle> getDependencyHandles(String id);

    /// @return A list of module handles specified module depends on.
    List<ModuleHandle> getDependentHandles(String id);

    /// Loads modules from a provided repository.
    /// @throws CandidateListException when repository failed to list candidates.
    /// @return Diagnostics of loading process.
    Diagnostics<ModuleLoadDiagnostic> load(ModuleRepository repository) throws CandidateListException;

    /// Starts specified module.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleStartException if an error occurred during module startup.
    void start(String id) throws ModuleStartException;

    /// Starts all modules, that have not started yet.
    /// @return Diagnostics of starting process.
    Diagnostics<ModuleStartDiagnostic> startAll();

    /// Tries to stop a specified module using a concrete group selection strategy.
    /// Modules are kept in memory, but its context is disposed.
    /// Module must be started before calling stop.
    /// @return module stop command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleStopException if an error occurred during module unloading.
    ModuleBatchCommand<Diagnostics<ModuleStopDiagnostic>> stop(String id, ModuleSelectionStrategy strategy) throws ModuleStopException;

    /// Tries to unload a specified module using a concrete group selection strategy.
    /// Modules are completely unloaded from JVM.
    /// Module must be stopped or just loaded before calling unload.
    /// @return module unload command.
    /// @throws IllegalArgumentException if module not found.
    /// @throws ModuleUnloadException if an error occurred during module unloading.
    ModuleBatchCommand<ModuleUnloadResult> unload(String id, ModuleSelectionStrategy strategy) throws ModuleUnloadException;

}
