package me.bottdev.meshdi.moduleit.core.repository;

import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleRepository;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record CompositeModuleRepository(List<ModuleRepository> repositories) implements ModuleRepository {

    public CompositeModuleRepository(ModuleRepository... repositories) {
        this(List.of(repositories));
    }

    @Override
    public List<ModuleCandidate> candidates() throws CandidateListException {
        List<ModuleCandidate> allCandidates = new ArrayList<>();
        for (ModuleRepository repository : repositories) {
            allCandidates.addAll(repository.candidates());
        }
        return allCandidates;
    }
}
