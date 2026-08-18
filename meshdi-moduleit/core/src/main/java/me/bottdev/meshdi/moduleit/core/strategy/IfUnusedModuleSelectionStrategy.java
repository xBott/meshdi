package me.bottdev.meshdi.moduleit.core.strategy;

import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleManager;
import me.bottdev.meshdi.moduleit.api.ModuleSelectionStrategy;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;

import java.util.List;

/// Implementation of [ModuleSelectionStrategy] that can select module
/// only if there are no modules that depend on it.
public class IfUnusedModuleSelectionStrategy implements ModuleSelectionStrategy {

    @Override
    public List<ModuleHandle> select(String id, ModuleManager manager) throws ModuleSelectionException {

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
