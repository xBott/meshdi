package me.bottdev.meshdi.moduleit.api.classprovider;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

public interface ClassProvider extends Comparable<ClassProvider> {

    /// Defines the priority of this provider.
    /// Higher values mean higher priority (checked first).
    int priority();

    @Override
    default int compareTo(ClassProvider other) {
        return Integer.compare(other.priority(), this.priority()); // Descending order
    }

    /// Attempts to provide the class with the specified name.
    /// @return the class if found, or null if this provider cannot provide it.
    /// @throws ClassNotFoundException if the provider definitively owns the package/class but failed to load it.
    Class<?> provideClass(String name) throws ClassNotFoundException;

    /// Attempts to provide the resource with the specified name.
    /// @return the resource URL if found, or null if this provider cannot provide it.
    URL provideResource(String name);

    /// Attempts to provide multiple resources with the specified name.
    /// @return an enumeration of resource URLs, or null if this provider cannot provide them.
    Enumeration<URL> provideResources(String name) throws IOException;

}
