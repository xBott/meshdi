package me.bottdev.meshdi.moduleit.api;

import lombok.NonNull;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import org.semver4j.Semver;
import org.semver4j.range.RangeList;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// An immutable [ModuleDescriptor] that only carries data.
///
/// This is what [ModuleDescriptorCodec] produces when it reads `META-INF/meshdi-module`,
/// so describing a module never requires loading or running code from its jar.
///
/// @param id the module id
/// @param semver the module version
/// @param apiVersion the range of host API versions the module accepts
/// @param exports packages the module exports to its dependents
/// @param repositories repositories the module declares for its isolated libraries
/// @param libraries libraries the module requires
/// @param versionedDependencies modules this module depends on
public record SimpleModuleDescriptor(
        @NonNull String id,
        @NonNull Semver semver,
        @NonNull RangeList apiVersion,
        @NonNull Set<String> exports,
        @NonNull Set<RepositoryDeclaration> repositories,
        @NonNull Set<LibraryRequirement> libraries,
        @NonNull List<VersionedDependencyRequest<String>> versionedDependencies
) implements ModuleDescriptor {

    public SimpleModuleDescriptor {
        if (id.isBlank()) throw new IllegalArgumentException("Module id must not be blank");
        exports = Collections.unmodifiableSet(new LinkedHashSet<>(exports));
        repositories = Collections.unmodifiableSet(new LinkedHashSet<>(repositories));
        libraries = Collections.unmodifiableSet(new LinkedHashSet<>(libraries));
        versionedDependencies = List.copyOf(versionedDependencies);
    }

    @Override
    public @NonNull List<VersionedDependencyRequest<String>> getVersionedDependencies() {
        return versionedDependencies;
    }

}
