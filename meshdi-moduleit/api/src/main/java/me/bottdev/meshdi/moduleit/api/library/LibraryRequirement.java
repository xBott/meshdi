package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;

/// A single library dependency declared by a module — parsed from @Module(libraries = {...})
/// via APT.
///
/// `coordinate` follows Maven GAV syntax: "groupId:artifactId:version".
/// Unlike ModuleDescriptor.getVersionedDependencies(), this does NOT participate
/// in the module dependency graph/lifecycle — libraries are leaves, resolved
/// separately by [MavenDependencyResolver], with their own transitive closure.
public record LibraryRequirement(
        @NonNull String coordinate,
        @NonNull LibraryScope scope
) {

    public LibraryRequirement {
        if (coordinate.isBlank()) {
            throw new IllegalArgumentException("Library coordinate must not be blank");
        }
    }

}
