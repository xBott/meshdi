package me.bottdev.meshdi.moduleit.core.selector;

import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleManager;
import me.bottdev.meshdi.moduleit.api.StartModuleSelector;
import me.bottdev.meshdi.moduleit.api.StopModuleSelector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Implementation of [StopModuleSelector] that
/// uses **DFS** to find cascade affected contexts in the mesh and returns
/// a reversed sub-list of topologically sorted contexts.
public class CascadeModuleSelector implements StartModuleSelector, StopModuleSelector {

    @Override
    public List<ModuleHandle> selectStart(String id, ModuleManager manager) {

        if (!manager.exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        Set<String> affected = new HashSet<>();
        selectDependenciesRecursive(id, manager, affected);

        return manager.getHandles().stream()
                .filter(handle -> affected.contains(handle.descriptor().id()))
                .toList();

    }

    private void selectDependenciesRecursive(
            String current,
            ModuleManager manager,
            Set<String> affected
    ) {
        if (!affected.add(current)) return;

        for (ModuleHandle dependency : manager.getDependencyHandles(current)) {
            selectDependentsRecursive(dependency.descriptor().id(), manager, affected);
        }

    }

    @Override
    public List<ModuleHandle> selectStop(String id, ModuleManager manager) {

        if (!manager.exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        Set<String> affected = new HashSet<>();
        selectDependentsRecursive(id, manager, affected);

        return manager.getHandles().reversed().stream()
                .filter(handle -> affected.contains(handle.descriptor().id()))
                .toList();

    }

    private void selectDependentsRecursive(
            String current,
            ModuleManager manager,
            Set<String> affected
    ) {

        if (!affected.add(current)) return;

        for (ModuleHandle dependent : manager.getDependentHandles(current)) {
            selectDependentsRecursive(dependent.descriptor().id(), manager, affected);
        }

    }

}
