package me.bottdev.meshdi.moduleit.core.utils;

import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.SemVersionParser;
import me.bottdev.kern.version.VersionRange;
import me.bottdev.kern.version.VersionRangeParser;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// A fake [ModuleDescriptor] for unit tests — no APT, no jar, plain data holder.
/// Build via {@link #builder(String)}.
public final class TestModuleDescriptor implements ModuleDescriptor {

    private final String id;
    private final SemVersion version;
    private final VersionRange apiVersion;
    private final Set<String> exports;
    private final List<VersionedDependencyRequest<String>> dependencies;

    private TestModuleDescriptor(Builder builder) {
        this.id = builder.id;
        this.version = builder.version;
        this.apiVersion = builder.apiVersion;
        this.exports = Set.copyOf(builder.exports);
        this.dependencies = List.copyOf(builder.dependencies);
    }

    @Override
    public String id() { return id; }

    @Override
    public SemVersion version() { return version; }

    @Override
    public VersionRange apiVersion() { return apiVersion; }

    @Override
    public Set<String> exports() { return exports; }

    @Override
    public List<VersionedDependencyRequest<String>> getVersionedDependencies() {
        return dependencies;
    }

    @Override
    public String toString() {
        return "TestModuleDescriptor[" + id + "@" + version + "]";
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final String id;
        private SemVersion version = SemVersionParser.parse("1.0.0");
        private VersionRange apiVersion = VersionRangeParser.parse("*"); // "любая" — подставь реальный wildcard-синтаксис
        private final Set<String> exports = new LinkedHashSet<>();
        private final List<VersionedDependencyRequest<String>> dependencies = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder version(String semver) {
            this.version = SemVersionParser.parse(semver);
            return this;
        }

        public Builder version(SemVersion version) {
            this.version = version;
            return this;
        }

        public Builder apiVersion(String range) {
            this.apiVersion = VersionRangeParser.parse(range);
            return this;
        }

        public Builder exports(String... packages) {
            this.exports.addAll(List.of(packages));
            return this;
        }

        /// Adds a REQUIRED, AFTER-ordered dependency — the common case
        /// (this module must be loaded/started after `dependencyId`).
        public Builder dependsOn(String dependencyId, String range) {
            return dependsOn(dependencyId, range, DependencyLink.REQUIRED);
        }

        public Builder dependsOn(String dependencyId, String range, DependencyLink link) {
            dependencies.add(new VersionedDependencyRequest<>(
                    dependencyId,
                    link,
                    DependOrder.AFTER,
                    VersionRangeParser.parse(range)
            ));
            return this;
        }

        /// Convenience for an optional dependency — won't fail resolution if missing.
        public Builder optionallyDependsOn(String dependencyId, String range) {
            return dependsOn(dependencyId, range, DependencyLink.OPTIONAL);
        }

        public TestModuleDescriptor build() {
            return new TestModuleDescriptor(this);
        }
    }
}