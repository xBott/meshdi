package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.LibraryScope;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import me.bottdev.meshdi.moduleit.core.library.MavenResolverLibraryLoader;
import me.bottdev.meshdi.moduleit.core.utils.FakeMavenRepository;
import me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.JarCandidate;
import me.bottdev.meshdi.moduleit.core.utils.TestJars;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static me.bottdev.meshdi.moduleit.core.utils.FakeMavenRepository.dependencies;
import static me.bottdev.meshdi.moduleit.core.utils.FakeMavenRepository.dependency;
import static me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/// Loads libraries through a real [SimpleModuleManager] and [MavenResolverLibraryLoader]
/// and checks which library classes module class loaders actually see.
class ModuleLibrariesIntegrationTest {

    static final String GREETING = "org.example.greeting.Greeting";
    static final String TOOL = "org.example.tool.Tool";

    @TempDir
    Path repositoryDir;

    @TempDir
    Path localRepository;

    @TempDir
    Path modulesDir;

    final List<Diagnostic> diagnostics = Collections.synchronizedList(new ArrayList<>());

    MavenResolverLibraryLoader loader;
    SimpleModuleManager manager;

    @BeforeEach
    void setUp() {
        FakeMavenRepository repository = new FakeMavenRepository(repositoryDir);
        repository
                .jar("org.example:greeting:1.0", "", greeting("1.0"))
                .jar("org.example:greeting:2.0", "", greeting("2.0"))
                .jar("org.example:tool:1.0", dependencies(dependency("org.example:greeting:1.0")), tool());

        ModuleLoadEnvironment environment = environment(diagnostics);
        loader = MavenResolverLibraryLoader.builder()
                .localRepository(localRepository)
                .repository(new RepositoryDeclaration("test", repository.url()))
                .build();
        manager = SimpleModuleManager.builder()
                .dependencyResolver(realResolver())
                .environment(environment)
                .libraryLoader(loader)
                .contextMesh(mock(ContextMesh.class))
                .leakDetector(mock(ModuleClassLoaderLeakDetector.class))
                .build();
    }

    @AfterEach
    void tearDown() {
        loader.close();
    }

    private static byte[] greeting(String version) {
        return TestJars.compile(Map.of(GREETING, """
                package org.example.greeting;
                public class Greeting {
                    public static String version() { return "%s"; }
                }
                """.formatted(version)), "org.example.greeting");
    }

    /// Tool is compiled against Greeting, but its jar contains only Tool.
    private static byte[] tool() {
        return TestJars.compile(Map.of(
                GREETING, """
                        package org.example.greeting;
                        public class Greeting {
                            public static String version() { return "compile-only"; }
                        }
                        """,
                TOOL, """
                        package org.example.tool;
                        public class Tool {
                            public static String version() {
                                return "tool on greeting " + org.example.greeting.Greeting.version();
                            }
                        }
                        """
        ), "org.example.tool");
    }

    private ModuleCandidate module(String id, LibraryRequirement... libraries) throws Exception {
        VirtualModuleDescriptor descriptor = VirtualModuleDescriptor.builder()
                .id(id)
                .semver(MODULE_VERSION)
                .libraries(new LinkedHashSet<>(List.of(libraries)))
                .build();
        Path jar = TestJars.emptyJar(modulesDir.resolve(id + ".jar"));
        return new JarCandidate(descriptor, jar.toUri().toURL());
    }

    private static LibraryRequirement shared(String gav) {
        return new LibraryRequirement(gav, LibraryScope.SHARED);
    }

    private static LibraryRequirement isolated(String gav) {
        return new LibraryRequirement(gav, LibraryScope.ISOLATED);
    }

    /// Resolves, prepares and loads the modules.
    /// @return result of the prepare step.
    private ModuleBatchResult<ModuleHandle> install(ModuleCandidate... candidates) throws Exception {
        manager.resolve(() -> List.of(candidates));
        ModuleBatchResult<ModuleHandle> prepared = manager.prepareAll().join();
        manager.loadAll();
        return prepared;
    }

    private Class<?> classIn(String moduleId, String className) throws Exception {
        return manager.getHandle(moduleId).classLoader().loadClass(className);
    }

    private String versionIn(String moduleId, String className) throws Exception {
        return (String) classIn(moduleId, className).getMethod("version").invoke(null);
    }

    @Test
    void shouldShareOneLibraryClass_betweenModules() throws Exception {
        // When
        install(module("a", shared("org.example:greeting:1.0")), module("b", shared("org.example:greeting:1.0")));

        // Then
        assertThat(versionIn("a", GREETING)).isEqualTo("1.0");
        assertThat(classIn("a", GREETING)).isSameAs(classIn("b", GREETING));
        assertThat(classIn("a", GREETING).getClassLoader()).isNotInstanceOf(ModuleClassLoader.class);
    }

    @Test
    @DisplayName("Child-first: a module's isolated library wins over another version in the shared class loader")
    void shouldPreferIsolatedLibrary_overSharedVersion() throws Exception {
        // When
        install(module("a", shared("org.example:greeting:1.0")), module("b", isolated("org.example:greeting:2.0")));

        // Then
        assertThat(versionIn("a", GREETING)).isEqualTo("1.0");
        assertThat(versionIn("b", GREETING)).isEqualTo("2.0");
        assertThat(classIn("b", GREETING).getClassLoader()).isSameAs(manager.getHandle("b").classLoader());
    }

    @Test
    @DisplayName("An isolated library finds its dependency in the shared class loader when versions match")
    void shouldLoadDependencyOfIsolatedLibrary_fromSharedClassLoader() throws Exception {
        // When
        install(module("a", shared("org.example:greeting:1.0")), module("b", isolated("org.example:tool:1.0")));

        // Then
        assertThat(versionIn("b", TOOL)).isEqualTo("tool on greeting 1.0");
        assertThat(classIn("b", GREETING)).isSameAs(classIn("a", GREETING));
        assertThat(manager.getHandle("b").libraries())
                .extracting(path -> path.getFileName().toString())
                .containsExactly("tool-1.0.jar");
    }

    @Test
    @DisplayName("A module added later gets the version already in the shared class loader, and the conflict is reported")
    void shouldKeepSharedVersion_whenModuleIsAddedLater() throws Exception {
        // Given
        install(module("a", shared("org.example:greeting:1.0")));

        // When
        install(module("b", shared("org.example:greeting:2.0")));

        // Then
        assertThat(versionIn("b", GREETING)).isEqualTo("1.0");
        assertThat(classIn("b", GREETING)).isSameAs(classIn("a", GREETING));
        assertThat(diagnostics).contains(new LibraryLoadDiagnostic.VersionConflictResolved(
                MavenCoordinate.of("org.example:greeting:1.0"),
                MavenCoordinate.of("org.example:greeting:2.0")
        ));
    }

    @Test
    @DisplayName("A missing library fails only its module; the others are prepared and loaded")
    void shouldLoadOtherModules_whenLibraryOfOneModuleIsMissing() throws Exception {
        // When
        ModuleBatchResult<ModuleHandle> prepared = install(
                module("broken", shared("org.example:missing:1.0")),
                module("healthy", shared("org.example:greeting:1.0"))
        );

        // Then
        assertThat(prepared).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
        assertThat(prepared.failed()).extracting(handle -> handle.descriptor().id()).containsExactly("broken");
        assertThat(manager.getHandle("broken").state()).isEqualTo(ModuleState.RESOLVED);
        assertThat(manager.getHandle("healthy").state()).isEqualTo(ModuleState.LOADED);
        assertThat(versionIn("healthy", GREETING)).isEqualTo("1.0");
    }

}
