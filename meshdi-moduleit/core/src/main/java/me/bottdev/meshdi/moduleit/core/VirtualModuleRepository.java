package me.bottdev.meshdi.moduleit.core;

import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleRepository;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;

import java.util.List;
import java.util.stream.Collectors;

public record VirtualModuleRepository(List<VirtualModuleHandle> virtualModules) implements ModuleRepository {

    public VirtualModuleRepository(VirtualModuleHandle... virtualModules) {
        this(List.of(virtualModules));
    }

    @Override
    public List<ModuleCandidate> candidates() throws CandidateListException {
        return virtualModules.stream()
                .map(VirtualModuleHandle::candidate)
                .collect(Collectors.toList());
    }
}
