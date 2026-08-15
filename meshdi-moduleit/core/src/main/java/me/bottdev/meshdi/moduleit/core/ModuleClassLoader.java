package me.bottdev.meshdi.moduleit.core;

import lombok.Getter;
import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.ModuleExportRegistry;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class ModuleClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private static final Set<String> PLATFORM_PREFIXES = Set.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun."
    );

    private final String moduleId;
    private final ClassLoader apiLoader;
    private final Set<String> apiPackages;
    private final ModuleExportRegistry exportRegistry;
    private final List<String> dependencyIds;

    @Getter
    private volatile boolean closed = false;

    public ModuleClassLoader(
            @NonNull String moduleId,
            @NonNull URL moduleURL,
            @NonNull ClassLoader apiLoader,
            @NonNull Set<String> apiPackages,
            @NonNull ModuleExportRegistry exportRegistry,
            @NonNull List<String> dependencyIds
    ) {
        super(new URL[]{moduleURL}, null);
        this.moduleId = moduleId;
        this.apiLoader = apiLoader;
        this.apiPackages = apiPackages;
        this.exportRegistry = exportRegistry;
        this.dependencyIds = dependencyIds;
    }

    private boolean isPlatformClass(String name) {
        for (String prefix : PLATFORM_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isApiPackage(String name) {
        int lastDot = name.lastIndexOf('.');
        String pkg = lastDot < 0 ? "" : name.substring(0, lastDot);
        return apiPackages.contains(pkg) || apiPackages.stream().anyMatch(pkg::startsWith);
    }

    private ClassNotFoundException enrichedNotFound(String name, ClassNotFoundException cause) {
        return new ClassNotFoundException(
                "Module '" + moduleId + "' cannot resolve class '" + name + "'. " +
                        "Checked: own jar, API loader, dependencies [" + dependencyIds + "].",
                cause
        );
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

        if (closed) {
            throw new IllegalStateException(
                    "ModuleClassLoader for '" + moduleId + "' is closed, cannot load '" + name + "'"
            );
        }

        synchronized (getClassLoadingLock(name)) {

            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                if (resolve) resolveClass(loaded);
                return loaded;
            }

            if (isPlatformClass(name)) {
                Class<?> clazz = findSystemClass(name);
                if (resolve) resolveClass(clazz);
                return clazz;
            }

            if (isApiPackage(name)) {
                Class<?> clazz = apiLoader.loadClass(name);
                if (resolve) resolveClass(clazz);
                return clazz;
            }

            String owner = exportRegistry.getIdByClassName(name);
            if (owner != null && !owner.equals(moduleId)) {
                ClassLoader ownerLoader = exportRegistry.getClassLoaderById(owner);
                Class<?> clazz = ownerLoader.loadClass(name);
                if (resolve) resolveClass(clazz);
                return clazz;
            }

            try {
                Class<?> clazz = findClass(name);
                if (resolve) resolveClass(clazz);
                return clazz;

            } catch (ClassNotFoundException ex) {
                throw enrichedNotFound(name, ex);
            }


        }

    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundException(
                    "Class '" + name + "' not found in module jar of '" + moduleId + "'", e
            );
        }
    }

    @Override
    public URL getResource(String name) {
        URL own = findResource(name);
        if (own != null) return own;

        return apiLoader.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> results = new ArrayList<>();

        findResource(name);
        Enumeration<URL> own = findResources(name);
        while (own.hasMoreElements()) results.add(own.nextElement());

        Enumeration<URL> fromApi = apiLoader.getResources(name);
        while (fromApi.hasMoreElements()) results.add(fromApi.nextElement());

        return Collections.enumeration(results);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        synchronized (this) {

            if (closed) return;
            closed = true;

            exportRegistry.unregister(moduleId);
            super.close();

        }
    }


}
