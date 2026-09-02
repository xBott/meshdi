package me.bottdev.meshdi.moduleit.api;

import org.semver4j.Semver;

import java.util.Set;

/// Data required to open a new module class loader
public interface ModuleLoadEnvironment {

    Semver apiVersion();

    ClassLoader apiLoader();

    Set<String> apiPackages();

    ModuleExportRegistry exportRegistry();

}
