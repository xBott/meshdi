package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.dependency.versioned.VersionedDependencyAware;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;

import java.util.List;

/// Representation of module that may be loaded. Also, can be understood as a physical module.
/// Allows to get [ModuleDescriptor] and the module's source URL.
public interface ModuleCandidate extends VersionedDependencyAware<String> {

    /// @return a special key that identifies the candidate physically, e.g. path of the jar.
    String sourceKey();

    /// @return module descriptor extracted from a physical candidate
    /// @throws ModuleDescriptorException when an error occurred while reading module descriptor of the candidate
    ModuleDescriptor descriptor();

    /// @return the URL pointing to the module's JAR file or primary source.
    java.net.URL sourceUrl();

    @Override
    default String dependencyKey() {
        return descriptor().dependencyKey();
    }

    @Override
    default SemVersion version() {
        return descriptor().version();
    }

    @Override
    default List<VersionedDependencyRequest<String>> getVersionedDependencies() {
        return descriptor().getVersionedDependencies();
    }
}
