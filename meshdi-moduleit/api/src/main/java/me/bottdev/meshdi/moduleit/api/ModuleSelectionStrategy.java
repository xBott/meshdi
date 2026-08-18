package me.bottdev.meshdi.moduleit.api;

import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;

import java.util.List;

/// Strategy that selects a group of modules to perform an operation.
public interface ModuleSelectionStrategy {

    List<ModuleHandle> select(String id, ModuleManager manager) throws ModuleSelectionException;

}
