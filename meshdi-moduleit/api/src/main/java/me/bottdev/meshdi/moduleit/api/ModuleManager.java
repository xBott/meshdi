package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleLoadException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleStartException;

import java.util.List;

/// Orchestrator of modules - **handle lifecycle of all modules**.
public interface ModuleManager {

    ModuleLoadEnvironment loadEnvironment();

    List<ModuleHandle> getHandles();

    boolean exists(String id);

    ModuleHandle getHandle(String id);

    /// Loads modules from a provided repository.
    /// @throws CandidateListException when repository failed to list candidates.
    /// @return Diagnostics of loading process.
    Diagnostics<ModuleDiagnostic> load(ModuleRepository repository) throws CandidateListException;

    /// Starts specified module.
    void start(String id) throws ModuleStartException;

}
