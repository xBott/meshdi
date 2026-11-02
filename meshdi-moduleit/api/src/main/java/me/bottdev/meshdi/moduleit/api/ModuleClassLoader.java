package me.bottdev.meshdi.moduleit.api;

import lombok.Getter;
import lombok.NonNull;

import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProviderContainer;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/// Class loader of a module. Its own URLs are the module jar and the module's isolated libraries.
///
/// Classes and resources are looked up in [ClassProvider] priority order, with the own URLs placed at
/// [#OWN_CLASSES_PRIORITY]: after platform, API and exported classes of other modules, but before
/// shared libraries. So a module's isolated library takes precedence over another version of the same
/// library in the shared class loader (child-first), while API types stay common to all modules.
public class ModuleClassLoader extends URLClassLoader {

    /// Position of the module's own jar and isolated libraries among the class providers.
    /// Providers with a higher priority are searched before them, providers with a lower one after them.
    public static final int OWN_CLASSES_PRIORITY = 750;

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

            boolean ownSearched = false;
            for (ClassProvider provider : providerContainer.providers()) {

                if (!ownSearched && provider.priority() < OWN_CLASSES_PRIORITY) {
                    ownSearched = true;
                    Class<?> own = findOwnClass(name);
                    if (own != null) {
                        if (resolve) resolveClass(own);
                        return own;
                    }
                }

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

            if (!ownSearched) {
                Class<?> own = findOwnClass(name);
                if (own != null) {
                    if (resolve) resolveClass(own);
                    return own;
                }
            }

            throw enrichedNotFound(name, new ClassNotFoundException(
                    "Class '" + name + "' not found in module jar of '" + moduleId + "'"
            ));

        }

    }

    /// @return class from the module jar or its isolated libraries, or null.
    private Class<?> findOwnClass(String name) {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException ex) {
            return null;
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
        boolean ownSearched = false;
        for (ClassProvider provider : providerContainer.providers()) {
            if (!ownSearched && provider.priority() < OWN_CLASSES_PRIORITY) {
                ownSearched = true;
                URL own = findResource(name);
                if (own != null) return own;
            }
            URL url = provider.provideResource(name);
            if (url != null) return url;
        }
        return ownSearched ? null : findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> results = new ArrayList<>();

        boolean ownSearched = false;
        for (ClassProvider provider : providerContainer.providers()) {
            if (!ownSearched && provider.priority() < OWN_CLASSES_PRIORITY) {
                ownSearched = true;
                addAll(results, findResources(name));
            }
            addAll(results, provider.provideResources(name));
        }
        if (!ownSearched) {
            addAll(results, findResources(name));
        }

        return Collections.enumeration(results);
    }

    private static void addAll(List<URL> target, Enumeration<URL> urls) {
        if (urls == null) return;
        while (urls.hasMoreElements()) {
            target.add(urls.nextElement());
        }
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
