package me.bottdev.meshdi.moduleit.api;

import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;

import java.util.List;

/// Strategy that selects a group of modules to perform a start operation.
public interface StartModuleSelector {

    List<ModuleHandle> selectStart(String id, ModuleManager manager) throws ModuleSelectionException;

}
