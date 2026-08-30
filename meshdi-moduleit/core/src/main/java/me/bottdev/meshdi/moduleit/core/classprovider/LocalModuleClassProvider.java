package me.bottdev.meshdi.moduleit.core.classprovider;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.function.Function;

public class LocalModuleClassProvider implements ClassProvider {

    private final String moduleId;
    private final Function<String, Class<?>> classFinder;
    private final Function<String, URL> resourceFinder;
    private final Function<String, Enumeration<URL>> resourcesFinder;

    public LocalModuleClassProvider(
            @NonNull String moduleId,
            @NonNull Function<String, Class<?>> classFinder,
            @NonNull Function<String, URL> resourceFinder,
            @NonNull Function<String, Enumeration<URL>> resourcesFinder
    ) {
        this.moduleId = moduleId;
        this.classFinder = classFinder;
        this.resourceFinder = resourceFinder;
        this.resourcesFinder = resourcesFinder;
    }

    @Override
    public int priority() {
        return 500;
    }

    @Override
    public Class<?> provideClass(String name) throws ClassNotFoundException {
        try {
            return classFinder.apply(name);

        } catch (Exception e) {
            if (e.getCause() instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) e.getCause();
            }
            throw new ClassNotFoundException("Class '" + name + "' not found locally in '" + moduleId + "'", e);

        }
    }

    @Override
    public URL provideResource(String name) {
        return resourceFinder.apply(name);
    }

    @Override
    public Enumeration<URL> provideResources(String name) throws IOException {
        return resourcesFinder.apply(name);
    }

}
