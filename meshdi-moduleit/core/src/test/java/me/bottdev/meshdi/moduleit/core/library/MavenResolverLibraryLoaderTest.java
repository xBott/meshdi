package me.bottdev.meshdi.moduleit.core.library;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleLibrariesResult;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.LibraryScope;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import me.bottdev.meshdi.moduleit.core.VirtualModuleDescriptor;
import me.bottdev.meshdi.moduleit.core.VirtualModuleHandle;
import me.bottdev.meshdi.moduleit.core.utils.FakeMavenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static me.bottdev.meshdi.moduleit.core.utils.FakeMavenRepository.*;
import static me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class MavenResolverLibraryLoaderTest {

    @TempDir
    Path repositoryDir;

    @TempDir
    Path localRepository;

    final List<Diagnostic> diagnostics = Collections.synchronizedList(new ArrayList<>());
    final DiagnosticSink<LibraryLoadDiagnostic> sink = diagnostics::add;

    FakeMavenRepository repository;
    MavenResolverLibraryLoader loader;

    @BeforeEach
    void setUp() {
        repository = new FakeMavenRepository(repositoryDir);
        loader = newLoader(false, new RepositoryDeclaration("test", repository.url()));
    }

    @AfterEach
    void tearDown() {
        loader.close();
    }

    private MavenResolverLibraryLoader newLoader(boolean offline, RepositoryDeclaration... repositories) {
        return MavenResolverLibraryLoader.builder()
                .localRepository(localRepository)
                .repositories(List.of(repositories))
                .offline(offline)
                .build();
    }

    private static LibraryRequirement shared(String gav) {
        return new LibraryRequirement(gav, LibraryScope.SHARED);
    }

    private static LibraryRequirement isolated(String gav) {
        return new LibraryRequirement(gav, LibraryScope.ISOLATED);
    }

    private static ModuleHandle module(String id, LibraryRequirement... libraries) {
        return module(id, Set.of(), libraries);
    }

    private static ModuleHandle module(String id, Set<RepositoryDeclaration> repositories, LibraryRequirement... libraries) {
        VirtualModuleDescriptor descriptor = VirtualModuleDescriptor.builder()
                .id(id)
                .semver(MODULE_VERSION)
                .libraries(new LinkedHashSet<>(List.of(libraries)))
                .repositories(repositories)
                .build();
        return new VirtualModuleHandle(descriptor, mockContext(id));
    }

    private ModuleLibrariesResult load(ModuleHandle... handles) {
        return loader.loadAll(List.of(handles), sink).orTimeout(30, TimeUnit.SECONDS).join();
    }

    private static List<String> fileNames(List<Path> paths) {
        return paths.stream().map(path -> path.getFileName().toString()).toList();
    }

    @Nested
    class SharedLibraries {

        @Test
        void shouldResolveTransitiveDependenciesIntoFlatList() {
            // Given
            repository.jar("org.example:a:1.0", dependencies(dependency("org.example:b:1.0")))
                    .jar("org.example:b:1.0", dependencies(dependency("org.example:c:1.0")))
                    .jar("org.example:c:1.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:a:1.0")));

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(fileNames(result.sharedLibraries()))
                    .containsExactlyInAnyOrder("a-1.0.jar", "b-1.0.jar", "c-1.0.jar");
            assertThat(result.sharedLibraries()).allSatisfy(path -> assertThat(path).startsWith(localRepository));
        }

        @Test
        void shouldSkipOptionalTestAndProvidedDependencies() {
            // Given
            repository.jar("org.example:a:1.0", dependencies(
                            optional("org.example:optional:1.0"),
                            scoped("org.example:test:1.0", "test"),
                            scoped("org.example:provided:1.0", "provided"),
                            scoped("org.example:runtime:1.0", "runtime")
                    ))
                    .jar("org.example:optional:1.0")
                    .jar("org.example:test:1.0")
                    .jar("org.example:provided:1.0")
                    .jar("org.example:runtime:1.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:a:1.0")));

            // Then
            assertThat(fileNames(result.sharedLibraries()))
                    .containsExactlyInAnyOrder("a-1.0.jar", "runtime-1.0.jar");
        }

        @Test
        @DisplayName("Scope from parent dependencyManagement applies (custom resolver pulled test jars of awssdk:s3)")
        void shouldApplyScopeFromDependencyManagement() {
            // Given
            repository.pom("org.example:parent:1.0", dependencyManagement(scoped("org.example:junit:1.0", "test")))
                    .jar("org.example:a:1.0", parent("org.example:parent:1.0")
                            + dependencies(dependency("org.example:junit")))
                    .jar("org.example:junit:1.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:a:1.0")));

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(fileNames(result.sharedLibraries())).containsExactly("a-1.0.jar");
        }

        @Test
        @DisplayName("${project.groupId} in a dependency is interpolated (custom resolver crashed on netty)")
        void shouldInterpolateProjectProperties() {
            // Given
            repository.jar("org.example:a:1.0", dependencies(dependency("${project.groupId}:b:${project.version}")))
                    .jar("org.example:b:1.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:a:1.0")));

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(fileNames(result.sharedLibraries())).containsExactlyInAnyOrder("a-1.0.jar", "b-1.0.jar");
        }

        @Test
        @DisplayName("Relocated artifacts are followed (custom resolver returned a missing mysql-connector-java jar)")
        void shouldFollowRelocation() {
            // Given
            repository.pom("org.old:lib:1.0", relocation("org.new", "lib"))
                    .jar("org.new:lib:1.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.old:lib:1.0")));

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(result.sharedLibraries()).singleElement()
                    .satisfies(path -> assertThat(path.toString()).contains("org/new/lib/1.0/lib-1.0.jar"));
        }

        @ParameterizedTest(name = "{0} vs {1}")
        @CsvSource({"1.0, 2.0", "2.0, 1.0", "1.9, 1.10"})
        @DisplayName("Highest version wins when modules request different versions, regardless of module order")
        void shouldPreferHighestVersion_whenModulesRequestDifferentVersions(String firstVersion, String secondVersion) {
            // Given
            repository.jar("org.example:lib:" + firstVersion).jar("org.example:lib:" + secondVersion);
            ModuleHandle first = module("first", shared("org.example:lib:" + firstVersion));
            ModuleHandle second = module("second", shared("org.example:lib:" + secondVersion));
            String highest = firstVersion.equals("2.0") ? "2.0" : secondVersion;
            String lowest = highest.equals(firstVersion) ? secondVersion : firstVersion;

            // When
            ModuleLibrariesResult result = load(first, second);

            // Then
            assertThat(fileNames(result.sharedLibraries())).containsExactly("lib-" + highest + ".jar");
            assertThat(diagnostics).contains(new LibraryLoadDiagnostic.VersionConflictResolved(
                    MavenCoordinate.of("org.example:lib:" + highest),
                    MavenCoordinate.of("org.example:lib:" + lowest)
            ));
        }

        @Test
        void shouldPreferDirectRequirement_overTransitiveVersion() {
            // Given
            repository.jar("org.example:a:1.0", dependencies(dependency("org.example:c:1.0")))
                    .jar("org.example:c:1.0")
                    .jar("org.example:c:2.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:a:1.0"), shared("org.example:c:2.0")));

            // Then
            assertThat(fileNames(result.sharedLibraries())).containsExactlyInAnyOrder("a-1.0.jar", "c-2.0.jar");
        }

        @Test
        void shouldUseRepositoryDeclaredByModule() {
            // Given
            FakeMavenRepository moduleRepository = new FakeMavenRepository(repositoryDir.resolve("module-repo"));
            moduleRepository.jar("org.example:only-there:1.0");
            ModuleHandle handle = module(
                    "m",
                    Set.of(new RepositoryDeclaration("module-repo", moduleRepository.url())),
                    shared("org.example:only-there:1.0")
            );

            // When
            ModuleLibrariesResult result = load(handle);

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(fileNames(result.sharedLibraries())).containsExactly("only-there-1.0.jar");
        }

        @Test
        void shouldResolveClassifier() {
            // Given
            repository.jar("org.example:native:1.0:linux");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:native:1.0:linux")));

            // Then
            assertThat(fileNames(result.sharedLibraries())).containsExactly("native-1.0-linux.jar");
        }

    }

    @Nested
    class SharedAcrossBatches {

        @Test
        @DisplayName("A version already in the shared class loader wins over a later request")
        void shouldKeepSharedVersion_whenLaterBatchRequestsAnother() {
            // Given
            repository.jar("org.example:lib:1.0").jar("org.example:lib:2.0");
            load(module("first", shared("org.example:lib:1.0")));
            diagnostics.clear();

            // When
            ModuleLibrariesResult result = load(module("second", shared("org.example:lib:2.0")));

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(fileNames(result.sharedLibraries())).containsExactly("lib-1.0.jar");
            assertThat(diagnostics).contains(new LibraryLoadDiagnostic.VersionConflictResolved(
                    MavenCoordinate.of("org.example:lib:1.0"),
                    MavenCoordinate.of("org.example:lib:2.0")
            ));
        }

        @Test
        void shouldAlignTransitiveDependencies_toAlreadySharedVersions() {
            // Given
            repository.jar("org.example:common:1.0")
                    .jar("org.example:common:2.0")
                    .jar("org.example:a:1.0", dependencies(dependency("org.example:common:2.0")));
            load(module("first", shared("org.example:common:1.0")));

            // When
            ModuleLibrariesResult result = load(module("second", shared("org.example:a:1.0")));

            // Then
            assertThat(fileNames(result.sharedLibraries())).containsExactlyInAnyOrder("a-1.0.jar", "common-1.0.jar");
        }

        @Test
        void shouldUseHighestVersion_whenLibraryWasNotSharedBefore() {
            // Given
            repository.jar("org.example:other:1.0").jar("org.example:lib:1.0").jar("org.example:lib:2.0");
            load(module("first", shared("org.example:other:1.0")));

            // When
            ModuleLibrariesResult result = load(
                    module("second", shared("org.example:lib:1.0")),
                    module("third", shared("org.example:lib:2.0"))
            );

            // Then
            assertThat(fileNames(result.sharedLibraries())).containsExactly("lib-2.0.jar");
        }

    }

    @Nested
    class IsolatedLibraries {

        @Test
        void shouldMapIsolatedLibrariesToTheirModule() {
            // Given
            repository.jar("org.example:x:1.0").jar("org.example:y:1.0");
            ModuleHandle first = module("first", isolated("org.example:x:1.0"));
            ModuleHandle second = module("second", isolated("org.example:y:1.0"));

            // When
            ModuleLibrariesResult result = load(first, second);

            // Then
            assertThat(result.sharedLibraries()).isEmpty();
            assertThat(fileNames(result.isolatedLibraries().get(first))).containsExactly("x-1.0.jar");
            assertThat(fileNames(result.isolatedLibraries().get(second))).containsExactly("y-1.0.jar");
        }

        @Test
        @DisplayName("A library already in the shared class loader is not loaded again for an isolated library")
        void shouldExcludeSharedLibraries_fromIsolatedDependencies() {
            // Given
            repository.jar("org.example:x:1.0", dependencies(dependency("org.example:common:1.0")))
                    .jar("org.example:common:1.0");
            ModuleHandle provider = module("provider", shared("org.example:common:1.0"));
            ModuleHandle consumer = module("consumer", isolated("org.example:x:1.0"));

            // When
            ModuleLibrariesResult result = load(provider, consumer);

            // Then
            assertThat(fileNames(result.sharedLibraries())).containsExactly("common-1.0.jar");
            assertThat(fileNames(result.isolatedLibraries().get(consumer))).containsExactly("x-1.0.jar");
        }

        @Test
        @DisplayName("A transitive dependency in another version than the shared one stays isolated (child-first)")
        void shouldKeepDifferentVersionOfSharedLibrary_inIsolatedLibraries() {
            // Given
            repository.jar("org.example:x:1.0", dependencies(dependency("org.example:common:2.0")))
                    .jar("org.example:common:1.0")
                    .jar("org.example:common:2.0");
            ModuleHandle provider = module("provider", shared("org.example:common:1.0"));
            ModuleHandle consumer = module("consumer", isolated("org.example:x:1.0"));

            // When
            ModuleLibrariesResult result = load(provider, consumer);

            // Then
            assertThat(fileNames(result.sharedLibraries())).containsExactly("common-1.0.jar");
            assertThat(fileNames(result.isolatedLibraries().get(consumer)))
                    .containsExactlyInAnyOrder("x-1.0.jar", "common-2.0.jar");
        }

        @Test
        @DisplayName("A library declared as isolated gets its own copy even if the same version is shared")
        void shouldKeepRequestedIsolatedLibrary_whenSameVersionIsShared() {
            // Given
            repository.jar("org.example:common:1.0");
            ModuleHandle provider = module("provider", shared("org.example:common:1.0"));
            ModuleHandle consumer = module("consumer", isolated("org.example:common:1.0"));

            // When
            ModuleLibrariesResult result = load(provider, consumer);

            // Then
            assertThat(fileNames(result.isolatedLibraries().get(consumer))).containsExactly("common-1.0.jar");
        }

        @Test
        void shouldReturnEmptyLists_whenModulesHaveNoLibraries() {
            // Given
            ModuleHandle handle = module("m");

            // When
            ModuleLibrariesResult result = load(handle);

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(result.sharedLibraries()).isEmpty();
            assertThat(result.isolatedLibraries()).containsEntry(handle, List.of());
        }

    }

    @Nested
    class Diagnostics {

        @Test
        @DisplayName("Malformed transitive POM: library is kept, its dependencies are dropped with a warning")
        void shouldWarn_whenTransitivePomIsMalformed() {
            // Given
            repository.jar("org.example:a:1.0", dependencies(dependency("org.example:b:1.0")))
                    .jar("org.example:b:1.0", dependencies(dependency("org.example:c:1.0")))
                    .jar("org.example:c:1.0");
            repository.breakPom("org.example:b:1.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:a:1.0")));

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(fileNames(result.sharedLibraries())).containsExactlyInAnyOrder("a-1.0.jar", "b-1.0.jar");
            assertThat(diagnostics)
                    .filteredOn(LibraryLoadDiagnostic.DescriptorUnavailable.class::isInstance)
                    .singleElement()
                    .satisfies(diagnostic -> {
                        assertThat(diagnostic.severity()).isEqualTo(DiagnosticSeverity.WARN);
                        assertThat(((LibraryLoadDiagnostic.DescriptorUnavailable) diagnostic).coordinate())
                                .isEqualTo(MavenCoordinate.of("org.example:b:1.0"));
                    });
        }

        @Test
        void shouldWarn_whenTransitivePomIsMissing() {
            // Given
            repository.jar("org.example:a:1.0", dependencies(dependency("org.example:b:1.0")))
                    .jar("org.example:b:1.0", dependencies(dependency("org.example:c:1.0")))
                    .jar("org.example:c:1.0");
            repository.removePom("org.example:b:1.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:a:1.0")));

            // Then
            assertThat(fileNames(result.sharedLibraries())).containsExactlyInAnyOrder("a-1.0.jar", "b-1.0.jar");
            assertThat(diagnostics).contains(new LibraryLoadDiagnostic.DescriptorUnavailable(
                    MavenCoordinate.of("org.example:b:1.0"),
                    "POM not found"
            ));
        }

        @Test
        void shouldReportDownloads_onlyWhenLibraryIsTransferred() {
            // Given
            repository.jar("org.example:a:1.0");
            ModuleHandle handle = module("m", shared("org.example:a:1.0"));

            // When
            load(handle);

            // Then
            assertThat(diagnostics).containsSubsequence(
                    new LibraryLoadDiagnostic.DownloadStarted(MavenCoordinate.of("org.example:a:1.0"), "test"),
                    new LibraryLoadDiagnostic.DownloadCompleted(MavenCoordinate.of("org.example:a:1.0"), "test")
            );

            // When: everything is cached now
            diagnostics.clear();
            load(handle);

            // Then
            assertThat(diagnostics)
                    .noneMatch(LibraryLoadDiagnostic.DownloadStarted.class::isInstance)
                    .noneMatch(LibraryLoadDiagnostic.DownloadCompleted.class::isInstance)
                    .contains(new LibraryLoadDiagnostic.Resolved(MavenCoordinate.of("org.example:a:1.0"), "shared"));
        }

        @Test
        void shouldReportResolvedLibraries_withTheirRequester() {
            // Given
            repository.jar("org.example:x:1.0", dependencies(dependency("org.example:common:1.0")))
                    .jar("org.example:common:1.0");
            ModuleHandle provider = module("provider", shared("org.example:common:1.0"));
            ModuleHandle consumer = module("consumer", isolated("org.example:x:1.0"));

            // When
            load(provider, consumer);

            // Then
            assertThat(diagnostics)
                    .filteredOn(LibraryLoadDiagnostic.Resolved.class::isInstance)
                    .containsExactlyInAnyOrder(
                            new LibraryLoadDiagnostic.Resolved(MavenCoordinate.of("org.example:common:1.0"), "shared"),
                            new LibraryLoadDiagnostic.Resolved(MavenCoordinate.of("org.example:x:1.0"), "consumer")
                    );
        }

        @Test
        @DisplayName("Events of concurrent loadAll calls reach only the sink of the call that caused them")
        void shouldRouteEvents_toSinkOfTheirOwnCall() {
            // Given
            repository.jar("org.example:broken:1.0", dependencies(dependency("org.example:b:1.0")))
                    .jar("org.example:b:1.0")
                    .jar("org.example:healthy:1.0");
            repository.removePom("org.example:b:1.0");

            List<LibraryLoadDiagnostic> brokenSink = Collections.synchronizedList(new ArrayList<>());
            List<LibraryLoadDiagnostic> healthySink = Collections.synchronizedList(new ArrayList<>());

            // When
            CompletableFuture<ModuleLibrariesResult> broken = loader.loadAll(
                    List.of(module("broken", shared("org.example:broken:1.0"))),
                    DiagnosticSink.forwarding(brokenSink::add)
            );
            CompletableFuture<ModuleLibrariesResult> healthy = loader.loadAll(
                    List.of(module("healthy", shared("org.example:healthy:1.0"))),
                    DiagnosticSink.forwarding(healthySink::add)
            );
            CompletableFuture.allOf(broken, healthy).orTimeout(30, TimeUnit.SECONDS).join();

            // Then
            assertThat(brokenSink).hasAtLeastOneElementOfType(LibraryLoadDiagnostic.DescriptorUnavailable.class);
            assertThat(healthySink)
                    .noneMatch(LibraryLoadDiagnostic.DescriptorUnavailable.class::isInstance)
                    .allSatisfy(diagnostic -> assertThat(diagnostic.toString()).doesNotContain("broken"));
        }

    }

    @Nested
    class Failures {

        @Test
        @DisplayName("A broken transitive dependency fails only the modules that requested the library pulling it in")
        void shouldFailOnlyModulesRequestingTheBrokenLibrary() {
            // Given
            repository.jar("org.example:a:1.0", dependencies(dependency("org.example:jar-missing:1.0")))
                    .pom("org.example:jar-missing:1.0", "")
                    .jar("org.example:b:1.0");
            ModuleHandle broken = module("broken", shared("org.example:a:1.0"));
            ModuleHandle healthy = module("healthy", shared("org.example:b:1.0"));

            // When
            ModuleLibrariesResult result = load(broken, healthy);

            // Then
            assertThat(result.failedModules()).containsExactly(broken);
            assertThat(fileNames(result.sharedLibraries())).contains("b-1.0.jar");
        }

        @Test
        void shouldFailEveryRequester_whenSharedLibraryIsMissing() {
            // Given
            ModuleHandle first = module("first", shared("org.example:missing:1.0"));
            ModuleHandle second = module("second", shared("org.example:missing:1.0"));

            // When
            ModuleLibrariesResult result = load(first, second);

            // Then
            assertThat(result.failedModules()).containsExactlyInAnyOrder(first, second);
        }

        @Test
        void shouldFailOnlyItsModule_whenIsolatedLibraryIsMissing() {
            // Given
            repository.jar("org.example:x:1.0");
            ModuleHandle broken = module("broken", isolated("org.example:missing:1.0"));
            ModuleHandle healthy = module("healthy", isolated("org.example:x:1.0"));

            // When
            ModuleLibrariesResult result = load(broken, healthy);

            // Then
            assertThat(result.failedModules()).containsExactly(broken);
            assertThat(fileNames(result.isolatedLibraries().get(healthy))).containsExactly("x-1.0.jar");
        }

        @Test
        void shouldReportFailure_whenLibraryDoesNotExist() {
            // Given
            ModuleHandle handle = module("m", shared("org.example:missing:1.0"));

            // When
            ModuleLibrariesResult result = load(handle);

            // Then
            assertThat(result.failedModules()).containsExactly(handle);
            assertThat(result.sharedLibraries()).isEmpty();
            assertThat(diagnostics).anySatisfy(diagnostic ->
                    assertThat(diagnostic.severity()).isEqualTo(DiagnosticSeverity.ERROR));
        }

        @Test
        void shouldStillResolveIsolatedLibraries_whenSharedResolutionFails() {
            // Given
            repository.jar("org.example:x:1.0");
            ModuleHandle broken = module("broken", shared("org.example:missing:1.0"));
            ModuleHandle healthy = module("healthy", isolated("org.example:x:1.0"));

            // When
            ModuleLibrariesResult result = load(broken, healthy);

            // Then
            assertThat(result.failedModules()).containsExactly(broken);
            assertThat(fileNames(result.isolatedLibraries().get(healthy))).containsExactly("x-1.0.jar");
        }

        @Test
        void shouldReportFailure_whenChecksumDoesNotMatch() {
            // Given
            repository.jar("org.example:a:1.0");
            repository.corruptJar("org.example:a:1.0");

            // When
            ModuleLibrariesResult result = load(module("m", shared("org.example:a:1.0")));

            // Then
            assertThat(result.failed()).isTrue();
            assertThat(result.sharedLibraries()).isEmpty();
            assertThat(diagnostics).hasAtLeastOneElementOfType(LibraryLoadDiagnostic.DownloadFailed.class);
        }

        @Test
        void shouldResolveFromLocalRepository_whenOffline() {
            // Given
            repository.jar("org.example:a:1.0", dependencies(dependency("org.example:b:1.0")))
                    .jar("org.example:b:1.0");
            load(module("m", shared("org.example:a:1.0")));

            // When
            ModuleLibrariesResult result;
            try (MavenResolverLibraryLoader offline = newLoader(true, new RepositoryDeclaration("test", repository.url()))) {
                result = offline.loadAll(List.of(module("m", shared("org.example:a:1.0"))), sink)
                        .orTimeout(30, TimeUnit.SECONDS)
                        .join();
            }

            // Then
            assertThat(result.failed()).isFalse();
            assertThat(fileNames(result.sharedLibraries())).containsExactlyInAnyOrder("a-1.0.jar", "b-1.0.jar");
        }

        @Test
        void shouldFailOffline_whenLibraryWasNeverDownloaded() {
            // Given
            repository.jar("org.example:a:1.0");

            // When
            ModuleLibrariesResult result;
            try (MavenResolverLibraryLoader offline = newLoader(true, new RepositoryDeclaration("test", repository.url()))) {
                result = offline.loadAll(List.of(module("m", shared("org.example:a:1.0"))), sink)
                        .orTimeout(30, TimeUnit.SECONDS)
                        .join();
            }

            // Then
            assertThat(result.failed()).isTrue();
        }

    }

}
