package me.bottdev.meshdi.moduleit.core;

import lombok.Builder;
import lombok.NonNull;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import org.semver4j.Semver;
import org.semver4j.range.RangeList;
import org.semver4j.range.RangeListFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Builder
public final class VirtualModuleDescriptor implements ModuleDescriptor {

    private final @NonNull String id;
    private final @NonNull Semver semver;

    @Builder.Default
    private final @NonNull RangeList apiVersion = RangeListFactory.create(">=0.0.0");

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
    public Semver semver() {
        return semver;
    }

    @Override
    public RangeList apiVersion() {
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
                Objects.equals(this.semver, that.semver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, semver);
    }

    @Override
    public String toString() {
        return "VirtualModuleDescriptor[" +
                "id=" + id + ", " +
                "version=" + semver + ']';
    }
}
