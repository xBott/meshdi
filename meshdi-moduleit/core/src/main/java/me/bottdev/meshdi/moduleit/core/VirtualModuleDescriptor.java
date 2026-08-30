package me.bottdev.meshdi.moduleit.core;

import lombok.Builder;
import lombok.NonNull;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.VersionRange;
import me.bottdev.kern.version.VersionRangeParser;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Builder
public final class VirtualModuleDescriptor implements ModuleDescriptor {

    private final @NonNull String id;
    private final @NonNull SemVersion version;

    @Builder.Default
    private final @NonNull VersionRange apiVersion = VersionRangeParser.parse("*");

    @Builder.Default
    private final @NonNull Set<String> exports = Collections.emptySet();

    @Builder.Default
    private final @NonNull Set<RepositoryDeclaration> repositories = Collections.emptySet();

    @Builder.Default
    private final @NonNull Set<LibraryRequirement> libraries = Collections.emptySet();

    @Builder.Default
    private final @NonNull List<VersionedDependencyRequest<String>> versionedDependencies = Collections.emptyList();

    @Override
    public @NonNull String id() {
        return id;
    }

    @Override
    public @NonNull SemVersion version() {
        return version;
    }

    @Override
    public VersionRange apiVersion() {
        return apiVersion;
    }

    @Override
    public Set<String> exports() {
        return exports;
    }

    @Override
    public Set<RepositoryDeclaration> repositories() {
        return repositories;
    }

    @Override
    public Set<LibraryRequirement> libraries() {
        return libraries;
    }

    @Override
    public @NonNull List<VersionedDependencyRequest<String>> getVersionedDependencies() {
        return versionedDependencies;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (VirtualModuleDescriptor) obj;
        return Objects.equals(this.id, that.id) &&
                Objects.equals(this.version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }

    @Override
    public String toString() {
        return "VirtualModuleDescriptor[" +
                "id=" + id + ", " +
                "version=" + version + ']';
    }
}
