package me.bottdev.meshdi.moduleit.core.library;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleLibrariesResult;
import me.bottdev.meshdi.moduleit.api.ModuleLibraryLoader;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.LibraryScope;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionScheme;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/// Loads module libraries with [Apache Maven Resolver](https://maven.apache.org/resolver/) -
/// the dependency resolution engine of Maven itself.
///
/// Every resolution produces a flat list of jars: the requested libraries together with all of
/// their runtime transitive dependencies. Parent POMs, BOM imports, property interpolation,
/// `dependencyManagement` (including managed scopes), exclusions, optional dependencies,
/// relocations, profiles, version ranges, snapshots and checksums are handled by Maven Resolver.
///
/// **Shared libraries** of all modules in a batch are resolved together as a single graph.
/// - If modules request different versions of the same library, the highest version wins
///   (compared with Maven version rules) and the conflict is reported.
/// - The shared class loader can only grow, so versions handed out as shared stay fixed for the
///   lifetime of the loader. Later batches are resolved against them: requested and transitive
///   versions are aligned to the already shared ones, and differing requests are reported as conflicts.
///
/// **Isolated libraries** are resolved per module, independently of shared ones, and are loaded
/// by the module's own class loader, which searches them before the shared class loader.
/// Transitive dependencies identical to an already shared artifact are left to the shared class loader,
/// so the module and shared libraries use the same classes for them.
///
/// **Failures** are attributed to modules: a module fails only if one of its own libraries or their
/// dependencies could not be resolved. Diagnostics, including events from inside Maven Resolver,
/// are reported to the sink passed to [#loadAll(List, DiagnosticSink)].
public class MavenResolverLibraryLoader implements ModuleLibraryLoader, AutoCloseable {

    /// `requestedBy` value used in diagnostics of shared library resolution.
    public static final String SHARED = "shared";

    /// Synthetic project that declares the requested libraries as its direct dependencies.
    /// Using a separate root (instead of making a library the root) keeps Maven semantics
    /// for optional dependencies: optional dependencies of a library are not included.
    private static final Artifact ROOT = new DefaultArtifact("me.bottdev.meshdi:module-libraries:0");

    private static final VersionScheme VERSION_SCHEME = new GenericVersionScheme();

    /// Attached to the [RequestTrace] of every resolution, so that Maven Resolver events
    /// can be routed to the sink of the [#loadAll(List, DiagnosticSink)] call that caused them.
    private record TracedSink(DiagnosticSink<LibraryLoadDiagnostic> sink) {}

    /// A requested library after a single version was selected, with the modules that requested it.
    private record Requirement(Artifact artifact, Set<ModuleHandle> requesters) {}

    /// @param artifacts   successfully resolved artifacts, in class path order.
    /// @param failedRoots versionless ids of requested libraries whose graph had failures.
    private record Resolution(List<Artifact> artifacts, Set<String> failedRoots) {}

    private record SharedOutcome(List<Path> paths, Map<String, Artifact> shared) {}

    private final RepositorySystem system;
    private final RepositorySystemSession.CloseableSession session;
    private final List<RemoteRepository> repositories;
    private final ExecutorService executor;

    /// Artifacts handed out as shared so far, by versionless id. Guarded by [#sharedLock].
    private final Map<String, Artifact> sharedArtifacts = new LinkedHashMap<>();
    private final ReentrantLock sharedLock = new ReentrantLock();

    /// @param localRepository directory of the local repository (Maven `~/.m2/repository` layout)
    ///                        where downloaded artifacts are cached.
    /// @param repositories    remote repositories available to every module, in lookup order.
    ///                        Repositories declared by modules are added after them.
    /// @param offline         if true, only the local repository is used.
    @Builder
    public MavenResolverLibraryLoader(
            @NonNull Path localRepository,
            @Singular List<RepositoryDeclaration> repositories,
            boolean offline
    ) {
        this.system = new RepositorySystemSupplier().get();
        this.session = new SessionBuilderSupplier(system).get()
                .withLocalRepositoryBaseDirectories(localRepository)
                .setSystemProperties(System.getProperties())
                .setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL)
                .setOffline(offline)
                .setRepositoryListener(new SinkRoutingListener())
                .build();
        this.repositories = repositories.stream()
                .map(MavenResolverLibraryLoader::toRemoteRepository)
                .toList();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public CompletableFuture<ModuleLibrariesResult> loadAll(
            @NonNull List<ModuleHandle> handles,
            @NonNull DiagnosticSink<LibraryLoadDiagnostic> sink
    ) {

        Set<ModuleHandle> failedModules = ConcurrentHashMap.newKeySet();

        return CompletableFuture
                .supplyAsync(() -> resolveShared(handles, sink, failedModules), executor)
                .thenCompose(shared -> {

                    Map<ModuleHandle, CompletableFuture<List<Path>>> isolatedFutures = new LinkedHashMap<>();
                    for (ModuleHandle handle : handles) {
                        isolatedFutures.put(handle, CompletableFuture.supplyAsync(
                                () -> resolveIsolated(handle, shared.shared(), sink, failedModules),
                                executor
                        ));
                    }

                    return CompletableFuture.allOf(isolatedFutures.values().toArray(CompletableFuture[]::new))
                            .thenApply(_ -> {
                                Map<ModuleHandle, List<Path>> isolated = new LinkedHashMap<>();
                                isolatedFutures.forEach((handle, future) -> isolated.put(handle, future.join()));
                                return new ModuleLibrariesResult(shared.paths(), isolated, failedModules);
                            });
                });
    }

    /// Resolves shared libraries of the batch against the libraries shared by earlier batches.
    /// Serialized, because every batch builds on the outcome of the previous one.
    private SharedOutcome resolveShared(
            List<ModuleHandle> handles,
            DiagnosticSink<LibraryLoadDiagnostic> sink,
            Set<ModuleHandle> failedModules
    ) {
        sharedLock.lock();
        try {
            Map<String, Requirement> requirements = selectRequirements(handles, LibraryScope.SHARED, sharedArtifacts, sink);

            List<Dependency> alreadyShared = sharedArtifacts.values().stream()
                    .map(artifact -> new Dependency(withoutPath(artifact), JavaScopes.RUNTIME))
                    .toList();

            Resolution resolution = resolve(SHARED, requirements, alreadyShared, repositoriesOf(handles), sink);

            List<Artifact> accepted = new ArrayList<>();
            for (Artifact artifact : resolution.artifacts()) {
                Artifact existing = sharedArtifacts.putIfAbsent(ArtifactIdUtils.toVersionlessId(artifact), artifact);
                if (existing != null && !existing.getVersion().equals(artifact.getVersion())) {
                    sink.accept(new LibraryLoadDiagnostic.VersionConflictResolved(toCoordinate(existing), toCoordinate(artifact)));
                    continue;
                }
                accepted.add(artifact);
                sink.accept(new LibraryLoadDiagnostic.Resolved(toCoordinate(artifact), SHARED));
            }

            for (String failedRoot : resolution.failedRoots()) {
                failedModules.addAll(requirements.get(failedRoot).requesters());
            }

            return new SharedOutcome(paths(accepted), Map.copyOf(sharedArtifacts));

        } finally {
            sharedLock.unlock();
        }
    }

    private List<Path> resolveIsolated(
            ModuleHandle handle,
            Map<String, Artifact> shared,
            DiagnosticSink<LibraryLoadDiagnostic> sink,
            Set<ModuleHandle> failedModules
    ) {
        String moduleId = handle.descriptor().id();
        Map<String, Requirement> requirements = selectRequirements(List.of(handle), LibraryScope.ISOLATED, Map.of(), sink);
        Resolution resolution = resolve(moduleId, requirements, List.of(), repositoriesOf(List.of(handle)), sink);

        if (!resolution.failedRoots().isEmpty()) {
            failedModules.add(handle);
        }

        List<Artifact> isolated = new ArrayList<>();
        for (Artifact artifact : resolution.artifacts()) {
            String key = ArtifactIdUtils.toVersionlessId(artifact);
            Artifact sharedArtifact = shared.get(key);
            boolean requested = requirements.containsKey(key);
            if (!requested && sharedArtifact != null && sharedArtifact.getVersion().equals(artifact.getVersion())) {
                continue;
            }
            isolated.add(artifact);
            sink.accept(new LibraryLoadDiagnostic.Resolved(toCoordinate(artifact), moduleId));
        }
        return paths(isolated);
    }

    /// Selects a single version of every library of the given scope requested by the modules.
    /// A version in `pinned` always wins; otherwise the highest requested version wins.
    /// @return requirements by versionless id, in the order libraries were first requested.
    private static Map<String, Requirement> selectRequirements(
            List<ModuleHandle> handles,
            LibraryScope scope,
            Map<String, Artifact> pinned,
            DiagnosticSink<LibraryLoadDiagnostic> sink
    ) {
        Map<String, Requirement> selected = new LinkedHashMap<>();
        for (ModuleHandle handle : handles) {
            for (LibraryRequirement library : handle.descriptor().libraries()) {
                if (library.scope() != scope) continue;

                MavenCoordinate coordinate = MavenCoordinate.of(library.coordinate());
                sink.accept(new LibraryLoadDiagnostic.Requested(coordinate));

                Artifact candidate = toArtifact(coordinate);
                String key = ArtifactIdUtils.toVersionlessId(candidate);

                Artifact pinnedArtifact = pinned.get(key);
                if (pinnedArtifact != null && !pinnedArtifact.getVersion().equals(candidate.getVersion())) {
                    sink.accept(new LibraryLoadDiagnostic.VersionConflictResolved(toCoordinate(pinnedArtifact), coordinate));
                    candidate = candidate.setVersion(pinnedArtifact.getVersion());
                }

                Requirement current = selected.get(key);
                if (current == null) {
                    selected.put(key, new Requirement(candidate, new LinkedHashSet<>(List.of(handle))));
                    continue;
                }

                current.requesters().add(handle);
                if (!current.artifact().getVersion().equals(candidate.getVersion())) {
                    boolean newer = compareVersions(candidate.getVersion(), current.artifact().getVersion()) > 0;
                    Artifact winner = newer ? candidate : current.artifact();
                    Artifact loser = newer ? current.artifact() : candidate;
                    selected.put(key, new Requirement(winner, current.requesters()));
                    sink.accept(new LibraryLoadDiagnostic.VersionConflictResolved(toCoordinate(winner), toCoordinate(loser)));
                }
            }
        }
        return selected;
    }

    /// Resolves the requirements together with their transitive runtime dependencies.
    /// @param managed versions that transitive dependencies must use.
    private Resolution resolve(
            String requestedBy,
            Map<String, Requirement> requirements,
            List<Dependency> managed,
            List<RemoteRepository> repositories,
            DiagnosticSink<LibraryLoadDiagnostic> sink
    ) {
        if (requirements.isEmpty()) return new Resolution(List.of(), Set.of());

        List<Dependency> roots = requirements.values().stream()
                .map(requirement -> new Dependency(requirement.artifact(), JavaScopes.RUNTIME))
                .toList();

        RequestTrace trace = new RequestTrace(new TracedSink(sink));
        CollectRequest collectRequest = new CollectRequest()
                .setTrace(trace)
                .setRootArtifact(ROOT)
                .setDependencies(roots)
                .setManagedDependencies(managed)
                .setRepositories(system.newResolutionRepositories(session, repositories));
        DependencyRequest request = new DependencyRequest(
                collectRequest,
                DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME)
        ).setTrace(trace);

        DependencyResult result;
        try {
            result = system.resolveDependencies(session, request);

        } catch (DependencyResolutionException ex) {
            result = ex.getResult();
            if (result == null || !hasReportableFailure(result)) {
                sink.accept(new LibraryLoadDiagnostic.ResolutionFailed(requestedBy, ex));
                return new Resolution(List.of(), requirements.keySet());
            }
        }

        for (Exception ex : result.getCollectExceptions()) {
            sink.accept(new LibraryLoadDiagnostic.ResolutionFailed(requestedBy, ex));
        }

        List<Artifact> resolved = new ArrayList<>();
        Set<String> failedIds = new HashSet<>();
        for (ArtifactResult artifactResult : result.getArtifactResults()) {
            if (artifactResult.isResolved()) {
                resolved.add(artifactResult.getArtifact());
            } else {
                Artifact artifact = artifactResult.getRequest().getArtifact();
                failedIds.add(ArtifactIdUtils.toId(artifact));
                Exception cause = artifactResult.getExceptions().isEmpty()
                        ? new IllegalStateException("Artifact was not found in any repository")
                        : artifactResult.getExceptions().getFirst();
                sink.accept(new LibraryLoadDiagnostic.DownloadFailed(toCoordinate(artifact), "all", cause));
            }
        }

        return new Resolution(resolved, failedRoots(result, requirements.keySet(), failedIds));
    }

    /// Finds requested libraries whose subtree contains a failure.
    /// Falls back to all of them when the failure cannot be attributed.
    private static Set<String> failedRoots(DependencyResult result, Set<String> requested, Set<String> failedIds) {
        boolean collectFailed = !result.getCollectExceptions().isEmpty();
        if (!collectFailed && failedIds.isEmpty()) return Set.of();
        if (collectFailed || result.getRoot() == null) return requested;

        Set<String> failed = new HashSet<>();
        for (DependencyNode child : result.getRoot().getChildren()) {
            if (!containsAny(child, failedIds, Collections.newSetFromMap(new IdentityHashMap<>()))) continue;

            failed.add(ArtifactIdUtils.toVersionlessId(child.getArtifact()));
            for (Artifact relocation : child.getRelocations()) {
                failed.add(ArtifactIdUtils.toVersionlessId(relocation));
            }
        }
        failed.retainAll(requested);
        return failed.isEmpty() ? requested : failed;
    }

    private static boolean containsAny(DependencyNode node, Set<String> ids, Set<DependencyNode> visited) {
        if (!visited.add(node)) return false;
        if (node.getArtifact() != null && ids.contains(ArtifactIdUtils.toId(node.getArtifact()))) return true;
        for (DependencyNode child : node.getChildren()) {
            if (containsAny(child, ids, visited)) return true;
        }
        return false;
    }

    private static boolean hasReportableFailure(DependencyResult result) {
        return !result.getCollectExceptions().isEmpty()
                || result.getArtifactResults().stream().anyMatch(artifactResult -> !artifactResult.isResolved());
    }

    private static int compareVersions(String first, String second) {
        try {
            return VERSION_SCHEME.parseVersion(first).compareTo(VERSION_SCHEME.parseVersion(second));
        } catch (InvalidVersionSpecificationException ex) {
            return first.compareTo(second);
        }
    }

    /// @return global repositories followed by repositories declared by the modules, without duplicate ids.
    private List<RemoteRepository> repositoriesOf(List<ModuleHandle> handles) {
        Map<String, RemoteRepository> byId = new LinkedHashMap<>();
        repositories.forEach(repository -> byId.putIfAbsent(repository.getId(), repository));
        for (ModuleHandle handle : handles) {
            for (RepositoryDeclaration declaration : handle.descriptor().repositories()) {
                byId.putIfAbsent(declaration.id(), toRemoteRepository(declaration));
            }
        }
        return List.copyOf(byId.values());
    }

    private static RemoteRepository toRemoteRepository(RepositoryDeclaration declaration) {
        return new RemoteRepository.Builder(declaration.id(), "default", declaration.url()).build();
    }

    private static Artifact toArtifact(MavenCoordinate coordinate) {
        return new DefaultArtifact(
                coordinate.groupId(),
                coordinate.artifactId(),
                coordinate.classifier(),
                coordinate.packaging(),
                coordinate.version()
        );
    }

    private static Artifact withoutPath(Artifact artifact) {
        return new DefaultArtifact(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getClassifier(),
                artifact.getExtension(),
                artifact.getVersion()
        );
    }

    private static MavenCoordinate toCoordinate(Artifact artifact) {
        return new MavenCoordinate(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getClassifier().isEmpty() ? null : artifact.getClassifier(),
                artifact.getExtension()
        );
    }

    private static List<Path> paths(List<Artifact> artifacts) {
        return artifacts.stream().map(Artifact::getPath).toList();
    }

    /// Translates Maven Resolver events into diagnostics of the sink found in the event's trace.
    private static final class SinkRoutingListener extends AbstractRepositoryListener {

        @Override
        public void artifactDescriptorMissing(RepositoryEvent event) {
            sinkOf(event).ifPresent(sink -> sink.accept(new LibraryLoadDiagnostic.DescriptorUnavailable(
                    toCoordinate(event.getArtifact()),
                    "POM not found"
            )));
        }

        @Override
        public void artifactDescriptorInvalid(RepositoryEvent event) {
            sinkOf(event).ifPresent(sink -> sink.accept(new LibraryLoadDiagnostic.DescriptorUnavailable(
                    toCoordinate(event.getArtifact()),
                    event.getException() != null ? event.getException().getMessage() : "invalid POM"
            )));
        }

        @Override
        public void artifactDownloading(RepositoryEvent event) {
            if (isPom(event)) return;
            sinkOf(event).ifPresent(sink -> sink.accept(new LibraryLoadDiagnostic.DownloadStarted(
                    toCoordinate(event.getArtifact()),
                    event.getRepository().getId()
            )));
        }

        /// Failures are not reported here: a miss in one repository is followed by a lookup in the next.
        /// Artifacts that are missing everywhere are reported from the resolution result.
        @Override
        public void artifactDownloaded(RepositoryEvent event) {
            if (event.getException() != null || isPom(event)) return;
            sinkOf(event).ifPresent(sink -> sink.accept(new LibraryLoadDiagnostic.DownloadCompleted(
                    toCoordinate(event.getArtifact()),
                    event.getRepository().getId()
            )));
        }

        /// POM transfers are part of building the graph; only library transfers are reported.
        private static boolean isPom(RepositoryEvent event) {
            return "pom".equals(event.getArtifact().getExtension());
        }

        private static Optional<DiagnosticSink<LibraryLoadDiagnostic>> sinkOf(RepositoryEvent event) {
            for (RequestTrace trace = event.getTrace(); trace != null; trace = trace.getParent()) {
                if (trace.getData() instanceof TracedSink(DiagnosticSink<LibraryLoadDiagnostic> sink)) {
                    return Optional.of(sink);
                }
            }
            return Optional.empty();
        }

    }

    @Override
    public void close() {
        executor.close();
        session.close();
        system.shutdown();
    }

}
