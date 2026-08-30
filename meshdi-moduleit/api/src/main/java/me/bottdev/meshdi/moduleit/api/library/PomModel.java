package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;
import lombok.ToString;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ToString
public final class PomModel {

    private final @NonNull MavenCoordinate self;
    private final MavenCoordinate parentCoordinate; // nullable

    private final Map<String, String> rawProperties;
    private final List<RawManagedDependency> rawDependencyManagementEntries;
    private final List<PomDependency> rawDependencies;

    // filled only after withResolvedContext(...) - empty by default
    private final Map<String, String> resolvedProperties;
    private final Map<String, String> resolvedDependencyManagement;

    public PomModel(
            @NonNull MavenCoordinate self,
            MavenCoordinate parentCoordinate,
            Map<String, String> rawProperties,
            List<RawManagedDependency> rawDependencyManagementEntries,
            List<PomDependency> rawDependencies,
            Map<String, String> resolvedProperties,
            Map<String, String> resolvedDependencyManagement
    ) {
        this.self = self;
        this.parentCoordinate = parentCoordinate;
        this.rawProperties = rawProperties;
        this.rawDependencyManagementEntries = rawDependencyManagementEntries;
        this.rawDependencies = rawDependencies;
        this.resolvedProperties = resolvedProperties;
        this.resolvedDependencyManagement = resolvedDependencyManagement;
    }



    /// Called by PomResolver once the full parent+BOM chain has been merged.
    /// Returns a NEW PomModel instance carrying the fully resolved context —
    /// original raw instance stays untouched (used for caching in PomResolver).
    public PomModel withResolvedContext(
            Map<String, String> mergedProperties,
            Map<String, String> mergedDependencyManagement
    ) {
        return new PomModel(self, parentCoordinate, rawProperties, rawDependencyManagementEntries,
                rawDependencies, Map.copyOf(mergedProperties), Map.copyOf(mergedDependencyManagement));
    }

    public MavenCoordinate self() { return self; }
    public MavenCoordinate parentCoordinate() { return parentCoordinate; }
    public Map<String, String> rawProperties() { return rawProperties; }
    public List<RawManagedDependency> rawDependencyManagementEntries() { return rawDependencyManagementEntries; }

    /// Flat groupId:artifactId -> version map (BOM entries already expanded).
    /// Populated only on the instance returned by PomResolver — calling this
    /// on a raw (not-yet-resolved) instance returns an empty map.
    public Map<String, String> rawDependencyManagement() { return resolvedDependencyManagement; }

    public String managedVersion(String groupId, String artifactId) {
        return resolvedDependencyManagement.get(groupId + ":" + artifactId);
    }

    /// Dependencies with versions/exclusions interpolated against the resolved
    /// property context (and falling back to dependencyManagement when a
    /// dependency doesn't specify its own version).
    public List<PomDependency> dependencies() {
        return rawDependencies.stream()
                .map(d -> d.withResolvedVersion(resolveVersionFor(d)))
                .toList();
    }

    private String resolveVersionFor(PomDependency dep) {
        String ownVersion = interpolate(dep.rawVersion());
        if (ownVersion != null) return ownVersion;
        return managedVersion(dep.groupId(), dep.artifactId());
    }

    private String interpolate(String raw) {

        if (raw == null || !raw.contains("${")) return raw;
        String result = raw;
        int MAX_PASSES = 5; // Prevent infinite loops for nested properties

        for (int i = 0; i < MAX_PASSES; i++) {

            if (!result.contains("${")) break;

            Matcher m = Pattern.compile("\\$\\{([^}]+)}").matcher(result);
            StringBuilder sb = new StringBuilder();
            boolean changed = false;

            while (m.find()) {

                String prop = m.group(1);
                String value = resolvedProperties.get(prop);

                if (value == null && prop.startsWith("pom.")) {
                    value = resolvedProperties.get("project." + prop.substring(4));
                }

                if (value != null) {
                    m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
                    changed = true;

                } else {
                    m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group()));

                }

            }

            m.appendTail(sb);
            if (!changed) break;
            result = sb.toString();

        }
        return result;
    }

    public record RawManagedDependency(
            String groupId, String artifactId, String version, String scope, String type
    ) {
        public boolean isBomImport() {
            return "import".equals(scope) && "pom".equals(type);
        }
    }

    public record PomDependency(
            String groupId,
            String artifactId,
            String rawVersion,
            String scope,
            boolean optional,
            Set<String> exclusions,
            String resolvedVersion
    ) {
        public PomDependency withResolvedVersion(String version) {
            return new PomDependency(groupId, artifactId, rawVersion, scope, optional, exclusions, version);
        }

        /// Used by MavenDependencyResolver — the version actually usable for resolution.
        public String version() { return resolvedVersion; }
    }



}