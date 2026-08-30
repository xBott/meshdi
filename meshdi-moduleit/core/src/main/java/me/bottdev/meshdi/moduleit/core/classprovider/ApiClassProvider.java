package me.bottdev.meshdi.moduleit.core.classprovider;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

public class ApiClassProvider implements ClassProvider {

    private final ClassLoader apiLoader;
    private final Set<String> apiPackages;

    public ApiClassProvider(
            @NonNull ClassLoader apiLoader,
            @NonNull Set<String> apiPackages
    ) {
        this.apiLoader = apiLoader;
        this.apiPackages = apiPackages;
    }

    @Override
    public int priority() {
        return 900;
    }

    private boolean isApiPackage(String name) {
        int lastDot = name.lastIndexOf('.');
        String pkg = lastDot < 0 ? "" : name.substring(0, lastDot);
        return apiPackages.contains(pkg) || apiPackages.stream().anyMatch(pkg::startsWith);
    }

    @Override
    public Class<?> provideClass(String name) throws ClassNotFoundException {
        if (isApiPackage(name)) {
            return apiLoader.loadClass(name);
        }
        return null;
    }

    @Override
    public URL provideResource(String name) {
        return apiLoader.getResource(name);
    }

    @Override
    public Enumeration<URL> provideResources(String name) throws IOException {
        return apiLoader.getResources(name);
    }

}
