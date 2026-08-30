package me.bottdev.meshdi.moduleit.core.classprovider;

import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

public class PlatformClassProvider implements ClassProvider {

    private static final Set<String> PLATFORM_PREFIXES = Set.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun."
    );

    @Override
    public int priority() {
        return 1000;
    }

    private boolean isPlatformClass(String name) {
        for (String prefix : PLATFORM_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    @Override
    public Class<?> provideClass(String name) throws ClassNotFoundException {
        if (isPlatformClass(name)) {
            return ClassLoader.getPlatformClassLoader().loadClass(name);
        }
        return null;
    }

    @Override
    public URL provideResource(String name) {
        return null;
    }

    @Override
    public Enumeration<URL> provideResources(String name) throws IOException {
        return null;
    }

}
