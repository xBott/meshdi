package me.bottdev.meshdi.moduleit.api;

import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;

import java.util.List;

/// Strategy that selects a group of modules to perform a stop or unlaoded operation.
public interface StopModuleSelector {

    List<ModuleHandle> selectStop(String id, ModuleManager manager) throws ModuleSelectionException;

}
