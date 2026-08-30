package me.bottdev.meshdi.moduleit.core.classprovider;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.ModuleExportRegistry;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

public class ExportRegistryClassProvider implements ClassProvider {

    private final String moduleId;
    private final ModuleExportRegistry exportRegistry;

    public ExportRegistryClassProvider(
            @NonNull String moduleId,
            @NonNull ModuleExportRegistry exportRegistry
    ) {
        this.moduleId = moduleId;
        this.exportRegistry = exportRegistry;
    }

    @Override
    public int priority() {
        return 800;
    }

    @Override
    public Class<?> provideClass(String name) throws ClassNotFoundException {
        String owner = exportRegistry.getIdByClassName(name);
        if (owner != null && !owner.equals(moduleId)) {
            ClassLoader ownerLoader = exportRegistry.getClassLoaderById(owner);
            if (ownerLoader != null) {
                return ownerLoader.loadClass(name);
            }
        }
        return null;
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
