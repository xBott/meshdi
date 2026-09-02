package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.exceptions.library.PomParseException;

import java.util.*;

public class MavenDependencyResolver {

    private record PendingRequest(
            MavenCoordinate coordinate,
            int depth,
            Set<String> exclusions,
            String requestedBy
    ) {}

    private final PomResolver pomResolver;

    public MavenDependencyResolver(
            @NonNull PomResolver pomResolver
    ) {
        this.pomResolver = pomResolver;
    }

    public List<ResolvedMavenDependency> resolve(
            Set<LibraryRequirement> rootRequirements,
            Set<String> rootExclusions,
            MavenResolutionContext context
    ) {

        Map<String, ResolvedMavenDependency> resolved = new LinkedHashMap<>();

        Queue<PendingRequest> queue = new ArrayDeque<>();
        for (LibraryRequirement requirement : rootRequirements) {

            MavenCoordinate coordinate = MavenCoordinate.of(requirement.coordinate());
            queue.add(new PendingRequest(
                    coordinate,
                    0,
                    rootExclusions,
                    "<root>"
            ));
            context.diagnosticsBuilder().append(
                    new LibraryLoadDiagnostic.Requested(coordinate)
            );

        }

        while (!queue.isEmpty()) {

            PendingRequest current = queue.poll();
            MavenCoordinate coordinate = current.coordinate();
            int currentDepth = current.depth();
            String currentRequestedBy = current.requestedBy();
            String key = current.coordinate().moduleKey();

            ResolvedMavenDependency existing = resolved.get(key);
            if (existing != null) {
                if (existing.depth() <= currentDepth) {
                    context.diagnosticsBuilder().append(
                            new LibraryLoadDiagnostic.VersionConflictResolved(existing.coordinate(), coordinate)
                    );
                    continue;
                }
            }

            PomModel pomModel;
            try {
                pomModel = pomResolver.resolveEffectivePom(coordinate, context);
            } catch (PomParseException ex) {
                context.diagnosticsBuilder().append(
                        new LibraryLoadDiagnostic.PomParseFailed(coordinate, ex)
                );
                continue;
            }

            resolved.put(key, new ResolvedMavenDependency(coordinate, currentDepth, currentRequestedBy));

            for (PomModel.PomDependency dependency : pomModel.dependencies()) {

                if (!isRuntimeRelevant(dependency.scope()) || dependency.optional()) continue;
                
                String dependencyKey = dependency.groupId() + ":" + dependency.artifactId();
                if (current.exclusions().contains(dependencyKey)) {
                    context.diagnosticsBuilder().append(
                            new LibraryLoadDiagnostic.DependencyExcluded(
                                    new MavenCoordinate(dependency.groupId(), dependency.artifactId(), dependency.version(), null, "jar"),
                                    currentRequestedBy
                            )
                    );
                    continue;
                }

                String version = dependency.version();
                if (version == null) {
                    context.diagnosticsBuilder().append(new LibraryLoadDiagnostic.VersionUnresolved(coordinate, dependencyKey));
                    continue;
                }

                Set<String> nextExclusions = new HashSet<>(current.exclusions());
                if (dependency.exclusions() != null) {
                    nextExclusions.addAll(dependency.exclusions());
                }

                MavenCoordinate childCoordinate = new MavenCoordinate(dependency.groupId(), dependency.artifactId(), version, null, "jar");
                context.diagnosticsBuilder().append(
                        new LibraryLoadDiagnostic.TransitiveDependencyFound(coordinate, childCoordinate)
                );

                queue.add(new PendingRequest(
                        childCoordinate,
                        currentDepth + 1,
                        nextExclusions,
                        coordinate.toString()
                ));

            }

        }

        return List.copyOf(resolved.values());

    }

    private boolean isRuntimeRelevant(String scope) {
        return scope == null || scope.equals("compile") || scope.equals("runtime");
    }

}
