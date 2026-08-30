package me.bottdev.meshdi.moduleit.api;

import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;

import java.util.List;

/// Strategy that selects a group of modules that depend on the provided one.
public interface DependentModuleSelector {

    List<ModuleHandle> selectDependents(String id, ModuleManager manager) throws ModuleSelectionException;

}
