package me.bottdev.meshdi.moduleit.core.classprovider;

import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

public class IsolatedLibraryClassProvider implements ClassProvider {

    private final URLClassLoader loader;

    public IsolatedLibraryClassProvider(URL[] isolatedUrls) {
        this.loader = new URLClassLoader(isolatedUrls, null);
    }

    @Override
    public Class<?> provideClass(String name) throws ClassNotFoundException {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public URL provideResource(String name) {
        return loader.getResource(name);
    }

    @Override
    public Enumeration<URL> provideResources(String name) throws IOException {
        return loader.getResources(name);
    }

    @Override
    public int priority() {
        return 600;
    }
}
