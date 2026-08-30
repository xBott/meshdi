package me.bottdev.meshdi.moduleit.core.selector;

import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleManager;
import me.bottdev.meshdi.moduleit.api.DependencyModuleSelector;
import me.bottdev.meshdi.moduleit.api.DependentModuleSelector;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;

import java.util.List;

/// Implementation of [DependencyModuleSelector] and [DependentModuleSelector] that can select module
/// only if there are no modules that depend on it.
public class IfUnusedModuleSelector implements DependencyModuleSelector, DependentModuleSelector {

    @Override
    public List<ModuleHandle> selectDependencies(String id, ModuleManager manager) throws ModuleSelectionException {
        if (!manager.exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        List<ModuleHandle> dependencies = manager.getDependencyHandles(id);
        if (!dependencies.isEmpty()) {
            List<String> dependencyIds = dependencies.stream().map(dependent -> dependent.descriptor().id()).toList();
            throw new ModuleSelectionException("Module \"" + id + "\" uses other modules: " + String.join(", ", dependencyIds));
        }

        return List.of(manager.getHandle(id));

    }

    @Override
    public List<ModuleHandle> selectDependents(String id, ModuleManager manager) throws ModuleSelectionException {

        if (!manager.exists(id))
            throw new IllegalArgumentException("Module \"" + id + "\" does not exist.");

        List<ModuleHandle> dependents = manager.getDependentHandles(id);
        if (!dependents.isEmpty()) {
            List<String> dependentIds = dependents.stream().map(dependent -> dependent.descriptor().id()).toList();
            throw new ModuleSelectionException("Module \"" + id + "\" is used by other modules: " + String.join(", ", dependentIds));
        }

        return List.of(manager.getHandle(id));
    }

}
