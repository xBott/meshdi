package me.bottdev.meshdi.moduleit.api;

import lombok.Getter;
import lombok.NonNull;

import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProviderContainer;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class ModuleClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final String moduleId;
    private final List<String> dependencyIds;
    private final ClassProviderContainer providerContainer;

    @Getter
    private volatile boolean closed = false;

    public ModuleClassLoader(
            @NonNull String moduleId,
            @NonNull URL[] moduleURLs,
            @NonNull ClassProviderContainer providerContainer,
            @NonNull List<String> dependencyIds
    ) {
        super(moduleURLs, null);
        this.moduleId = moduleId;
        this.providerContainer = providerContainer;
        this.dependencyIds = dependencyIds;
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

            for (ClassProvider provider : providerContainer.providers()) {
                try {
                    Class<?> clazz = provider.provideClass(name);
                    if (clazz != null) {
                        if (resolve) resolveClass(clazz);
                        return clazz;
                    }
                } catch (ClassNotFoundException ex) {
                    throw enrichedNotFound(name, ex);
                }
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
        for (ClassProvider provider : providerContainer.providers()) {
            URL url = provider.provideResource(name);
            if (url != null) return url;
        }
        return findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> results = new ArrayList<>();
        
        for (ClassProvider provider : providerContainer.providers()) {
            Enumeration<URL> urls = provider.provideResources(name);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    results.add(urls.nextElement());
                }
            }
        }
        
        Enumeration<URL> own = findResources(name);
        if (own != null) {
            while (own.hasMoreElements()) {
                results.add(own.nextElement());
            }
        }
        
        return Collections.enumeration(results);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        synchronized (this) {

            if (closed) return;
            closed = true;

            super.close();

        }
    }


}
