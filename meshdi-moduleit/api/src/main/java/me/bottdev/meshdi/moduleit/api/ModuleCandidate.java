package me.bottdev.meshdi.moduleit.api;

import lombok.NonNull;
import me.bottdev.kern.dependency.versioned.VersionedDependencyAware;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;

import java.util.List;

/// Representation of module that may be loaded. Also, can be understood as a physical module.
/// Allows to get [ModuleDescriptor] and the module's source URL.
public interface ModuleCandidate extends VersionedDependencyAware<String> {

    /// @return a special key that identifies the candidate physically, e.g. path of the jar.
    String sourceKey();

    /// @return module descriptor extracted from a physical candidate
    /// @throws ModuleDescriptorException when an cause occurred while reading module descriptor of the candidate
    ModuleDescriptor descriptor();

    /// @return the URL pointing to the module's JAR file or primary source.
    java.net.URL sourceUrl();

    @Override
    @NonNull
    default String dependencyKey() {
        return descriptor().dependencyKey();
    }

    @Override
    @NonNull
    default String version() {
        return descriptor().version();
    }

    @Override
    default List<VersionedDependencyRequest<String>> getVersionedDependencies() {
        return descriptor().getVersionedDependencies();
    }
}
