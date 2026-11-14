package me.bottdev.meshdi.moduleit.core.classprovider;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.ModuleExportRegistry;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;

import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;

/// Serves classes of packages exported by the modules this module declared as dependencies.
///
/// Exports of modules that are not declared dependencies are invisible: otherwise a module could link
/// against a module the manager does not know it uses, and that module would be stopped or unloaded
/// underneath it while its class loader stays pinned.
public class ExportRegistryClassProvider implements ClassProvider {

    private final String moduleId;
    private final Set<String> dependencyIds;
    private final ModuleExportRegistry exportRegistry;

    public ExportRegistryClassProvider(
            @NonNull String moduleId,
            @NonNull Collection<String> dependencyIds,
            @NonNull ModuleExportRegistry exportRegistry
    ) {
        this.moduleId = moduleId;
        this.dependencyIds = Set.copyOf(dependencyIds);
        this.exportRegistry = exportRegistry;
    }

    @Override
    public int priority() {
        return 800;
    }

    @Override
    public Class<?> provideClass(String name) throws ClassNotFoundException {
        String owner = exportRegistry.getIdByClassName(name);
        if (owner == null || owner.equals(moduleId) || !dependencyIds.contains(owner)) return null;

        ClassLoader ownerLoader = exportRegistry.getClassLoaderById(owner);
        if (ownerLoader == null) return null;

        return ownerLoader.loadClass(name);
    }

    @Override
    public URL provideResource(String name) {
        return null;
    }

    @Override
    public Enumeration<URL> provideResources(String name) {
        return null;
    }

}
