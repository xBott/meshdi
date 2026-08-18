package me.bottdev.meshdi.moduleit.core.strategy;

import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleManager;
import me.bottdev.meshdi.moduleit.api.ModuleSelectionStrategy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Implementation of [ModuleSelectionStrategy] that
/// uses **DFS** to find cascade affected contexts in the mesh and returns
/// a reversed sub-list of topologically sorted contexts.
public class CascadeModuleSelectionStrategy implements ModuleSelectionStrategy {

    @Override
    public List<ModuleHandle> select(String id, ModuleManager manager) {

        if (!manager.exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        Set<String> affected = new HashSet<>();
        selectRecursive(id, manager, affected);

        return manager.getHandles().reversed().stream()
                .filter(handle -> affected.contains(handle.descriptor().id()))
                .toList();

    }

    private void selectRecursive(
            String current,
            ModuleManager manager,
            Set<String> affected
    ) {

        if (!affected.add(current)) return;

        for (ModuleHandle dependent : manager.getDependentHandles(current)) {
            selectRecursive(dependent.descriptor().id(), manager, affected);
        }

    }


}
