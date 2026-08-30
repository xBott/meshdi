package me.bottdev.meshdi.moduleit.api.library;

import me.bottdev.meshdi.moduleit.api.exceptions.library.LibraryFetchException;
import me.bottdev.meshdi.moduleit.api.exceptions.library.PomParseException;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Resolves the EFFECTIVE POM for a coordinate: fetches the POM chain (self + all
/// parents), merges properties and dependencyManagement bottom-up (child overrides
/// parent), expands BOM imports (<scope>import</scope>, <type>pom</type>) inline
/// into dependencyManagement, and returns a single PomModel with everything already
/// interpolated and merged.
///
/// Caches parsed raw POMs by coordinate — a parent POM shared by many artifacts
/// (e.g. a company "parent" POM, or a BOM referenced by dozens of libraries)
/// is only fetched and parsed once per resolver instance.
public class PomResolver {

    /// @throws PomParseException if the POM (or any ancestor/BOM it references)
    /// cannot be fetched or parsed.
    public PomModel resolveEffectivePom(MavenCoordinate coordinate, MavenResolutionContext context) throws
            PomParseException
    {

        List<PomModel> chain = fetchParentChain(coordinate, new HashSet<>(), context);

        // chain is going from coordinate (leaf, index 0) to the furthest parent (last) —
        // reverse to merge from parent to child (child overrides parent)
        Collections.reverse(chain);

        Map<String, String> mergedProperties = new LinkedHashMap<>();
        Map<String, String> mergedDependencyManagement = new LinkedHashMap<>();

        for (PomModel pom : chain) {
            mergedProperties.putAll(pom.rawProperties());
            mergeDependencyManagementWithBomExpansion(pom, mergedProperties, mergedDependencyManagement, context);
        }

        PomModel leaf = chain.getLast(); // requested coordinate itself
        return leaf.withResolvedContext(mergedProperties, mergedDependencyManagement);
    }

    /// Fetches coordinate's raw POM, then recursively its <parent>, building the
    /// chain from leaf to root. `visiting` guards against a circular parent
    /// reference (broken but real-world POMs sometimes have this).
    private List<PomModel> fetchParentChain(MavenCoordinate coordinate, Set<String> visiting, MavenResolutionContext context) throws
            PomParseException
    {
        String visitKey = coordinate.moduleKey() + ":" + coordinate.version();
        if (!visiting.add(visitKey)) {
            throw new PomParseException("Circular parent reference detected at " + coordinate);
        }

        PomModel pom = fetchRawPom(coordinate, context);
        List<PomModel> chain = new ArrayList<>();
        chain.add(pom);

        MavenCoordinate parentCoord = pom.parentCoordinate();
        if (parentCoord != null) {
            chain.addAll(fetchParentChain(parentCoord, visiting, context));
        }

        return chain;
    }

    private PomModel fetchRawPom(MavenCoordinate coordinate, MavenResolutionContext context) throws PomParseException {
        String cacheKey = coordinate.moduleKey() + ":" + coordinate.version();

        Map<String, PomModel> rawPomCache = context.sharedPomCache();
        if (rawPomCache.containsKey(cacheKey)) return rawPomCache.get(cacheKey);

        try {
            Path pomPath = context.repositoryChain().fetchPom(coordinate)
                    .map(MavenRepositoryChain.FetchedFrom::value)
                    .orElseThrow(() -> new PomParseException("POM not found for " + coordinate));

            PomModel model = PomModelParser.parseRaw(pomPath);
            rawPomCache.put(cacheKey, model);

            return model;

        } catch (LibraryFetchException ex) {
            throw new PomParseException("Failed to fetch POM for " + coordinate, ex);

        }

    }

    /// Merges pom's own <dependencyManagement> into `target`, expanding any
    /// <scope>import</scope><type>pom</type> entries (BOMs) by recursively
    /// resolving THEIR effective dependencyManagement first and splicing it in.
    /// BOM entries are expanded before pom's own direct dependencyManagement
    /// entries are applied, so a pom can still override individual versions
    /// that its imported BOM also defines (matches real Maven semantics).
    private void mergeDependencyManagementWithBomExpansion(
            PomModel pom,
            Map<String, String> properties,
            Map<String, String> target,
            MavenResolutionContext context
    ) throws PomParseException {

        for (PomModel.RawManagedDependency managed : pom.rawDependencyManagementEntries()) {

            if (managed.isBomImport()) {
                String groupId = interpolate(managed.groupId(), properties);
                String artifactId = interpolate(managed.artifactId(), properties);
                String version = interpolate(managed.version(), properties);

                MavenCoordinate bomCoord = new MavenCoordinate(groupId, artifactId, version, null, "pom");
                // recursive call: BOM might have parent itself and/or import other BOMs
                PomModel bomEffective = resolveEffectivePom(bomCoord, context);
                target.putAll(bomEffective.rawDependencyManagement()); // уже смёрженный BOM целиком

            } else {
                String key = interpolate(managed.groupId(), properties) + ":" + interpolate(managed.artifactId(), properties);
                target.put(key, interpolate(managed.version(), properties));
            }
        }
    }

    private String interpolate(String raw, Map<String, String> properties) {
        if (raw == null || !raw.contains("${")) return raw;
        StringBuilder result = new StringBuilder();
        Matcher m = Pattern.compile("\\$\\{([^}]+)}").matcher(raw);
        while (m.find()) {
            String value = properties.get(m.group(1));
            m.appendReplacement(result, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(result);
        return result.toString();
    }

}
