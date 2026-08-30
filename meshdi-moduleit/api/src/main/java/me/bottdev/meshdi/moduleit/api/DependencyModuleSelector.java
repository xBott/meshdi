package me.bottdev.meshdi.moduleit.api;

import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;

import java.util.List;

/// Strategy that selects a group of modules, provided module depends on.
public interface DependencyModuleSelector {

    List<ModuleHandle> selectDependencies(String id, ModuleManager manager) throws ModuleSelectionException;

}
