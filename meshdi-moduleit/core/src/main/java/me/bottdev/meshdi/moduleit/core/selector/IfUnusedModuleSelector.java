package me.bottdev.meshdi.moduleit.core.selector;

import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleManager;
import me.bottdev.meshdi.moduleit.api.StartModuleSelector;
import me.bottdev.meshdi.moduleit.api.StopModuleSelector;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;

import java.util.List;

/// Implementation of [StartModuleSelector] and [StopModuleSelector] that can select module
/// only if there are no modules that depend on it.
public class IfUnusedModuleSelector implements StartModuleSelector, StopModuleSelector {

    @Override
    public List<ModuleHandle> selectStart(String id, ModuleManager manager) throws ModuleSelectionException {
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
    public List<ModuleHandle> selectStop(String id, ModuleManager manager) throws ModuleSelectionException {

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
