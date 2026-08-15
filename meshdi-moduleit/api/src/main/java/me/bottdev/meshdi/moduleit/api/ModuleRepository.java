package me.bottdev.meshdi.moduleit.api;

import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;

import java.util.List;

/// A source of modules. Provides a list of modules that can be loaded from this repository.
public interface ModuleRepository {

    /// Lists all possible module candidates.
    /// @return list of [ModuleCandidate].
    /// @throws CandidateListException when another exception occurred while listing the candidates.
    List<ModuleCandidate> candidates() throws CandidateListException;

}
