package me.bottdev.meshdi.moduleit.api;

import lombok.NonNull;
import me.bottdev.kern.dependency.versioned.VersionedDependencyAware;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import org.semver4j.Semver;
import org.semver4j.range.RangeList;

import java.util.Set;

/// A contract that contains a meta-information about the module.
/// Generated automatically thought APT for classes annotated with [me.bottdev.meshdi.moduleit.api.annotations.Module].
public interface ModuleDescriptor extends VersionedDependencyAware<String> {

    String DESCRIPTOR_FILE = "META-INF/meshdi-module";

    /// @return id of the module.
    String id();

    @Override
    @NonNull
    default String dependencyKey() {
        return id();
    }

    /// @return version of the module.
    Semver semver();

    /// @return version of the module in string format.
    @NonNull
    default String version() {
        return semver().toString();
    }

    /// @return required version range of the API.
    RangeList apiVersion();

    /// @return exported packages, which can be used by other modules.
    Set<String> exports();

    /// @return a set of module's custom repositories.
    Set<RepositoryDeclaration> repositories();

    /// @return a set of libraries required by the module.
    Set<LibraryRequirement> libraries();


}
