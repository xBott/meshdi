package me.bottdev.meshdi.moduleit.core;

import me.bottdev.meshdi.moduleit.api.ModuleSelectionStrategy;
import me.bottdev.meshdi.moduleit.core.strategy.CascadeModuleSelectionStrategy;
import me.bottdev.meshdi.moduleit.core.strategy.IfUnusedModuleSelectionStrategy;

public class ModuleSelectionStrategies {

    public static final ModuleSelectionStrategy IF_UNUSED = new IfUnusedModuleSelectionStrategy();
    public static final ModuleSelectionStrategy CASCADE = new CascadeModuleSelectionStrategy();

}
