package me.bottdev.meshdi.moduleit.core.classprovider;

import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

/// Serves JDK classes from the platform class loader.
///
/// Only `java.*` is reserved for the JDK. The other prefixes (`javax.`, `jdk.`, `sun.`, `com.sun.`)
/// are shared with ordinary libraries such as `javax.inject`, JSR-305 or JNA, so a miss in the
/// platform loader falls through to the next providers instead of failing the lookup.
public class PlatformClassProvider implements ClassProvider {

    private static final String RESERVED_PREFIX = "java.";

    private static final Set<String> PLATFORM_PREFIXES = Set.of(
            RESERVED_PREFIX, "javax.", "jdk.", "sun.", "com.sun."
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
        if (!isPlatformClass(name)) return null;
        try {
            return ClassLoader.getPlatformClassLoader().loadClass(name);
        } catch (ClassNotFoundException ex) {
            // Nobody else may define java.* classes, so the search ends here.
            if (name.startsWith(RESERVED_PREFIX)) throw ex;
            return null;
        }
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
