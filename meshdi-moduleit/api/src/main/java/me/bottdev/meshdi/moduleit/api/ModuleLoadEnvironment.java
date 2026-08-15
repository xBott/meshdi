package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.version.SemVersion;

import java.util.Set;

/// Data required to open a new module class loader
public interface ModuleLoadEnvironment {

    SemVersion apiVersion();

    ClassLoader apiLoader();

    Set<String> apiPackages();

    ModuleExportRegistry exportRegistry();

}
