package me.bottdev.meshdi.moduleit.core.classprovider;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

/// Serves classes and resources of the host's API packages.
///
/// A name belongs to the API if its package equals an API package or is a sub-package of one
/// (`me.bottdev.kern` covers `me.bottdev.kern.struct` but not `me.bottdev.kernel`).
/// Resources are filtered the same way, so module code cannot read the host's configuration files
/// and `ServiceLoader` does not pick up the host's service files (for example its component definitions).
public class ApiClassProvider implements ClassProvider {

    private final ClassLoader apiLoader;
    private final Set<String> apiPackages;
    /// The same packages as resource directory prefixes, e.g. `me/bottdev/kern/`.
    private final Set<String> apiResourcePrefixes;

    public ApiClassProvider(
            @NonNull ClassLoader apiLoader,
            @NonNull Set<String> apiPackages
    ) {
        this.apiLoader = apiLoader;
        this.apiPackages = Set.copyOf(apiPackages);
        this.apiResourcePrefixes = apiPackages.stream()
                .map(pkg -> pkg.replace('.', '/') + "/")
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public int priority() {
        return 900;
    }

    private boolean isApiClass(String name) {
        int lastDot = name.lastIndexOf('.');
        String pkg = lastDot < 0 ? "" : name.substring(0, lastDot);
        if (apiPackages.contains(pkg)) return true;
        for (String apiPackage : apiPackages) {
            if (pkg.length() > apiPackage.length()
                    && pkg.startsWith(apiPackage)
                    && pkg.charAt(apiPackage.length()) == '.') return true;
        }
        return false;
    }

    private boolean isApiResource(String name) {
        String normalized = name.startsWith("/") ? name.substring(1) : name;
        for (String prefix : apiResourcePrefixes) {
            if (normalized.startsWith(prefix)) return true;
        }
        return false;
    }

    @Override
    public Class<?> provideClass(String name) throws ClassNotFoundException {
        if (isApiClass(name)) {
            return apiLoader.loadClass(name);
        }
        return null;
    }

    @Override
    public URL provideResource(String name) {
        return isApiResource(name) ? apiLoader.getResource(name) : null;
    }

    @Override
    public Enumeration<URL> provideResources(String name) throws IOException {
        return isApiResource(name) ? apiLoader.getResources(name) : null;
    }

}
