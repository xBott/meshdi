package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.kern.commons.diagnostic.DiagnosticSinkFactory;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.core.mesh.DagContextMesh;
import me.bottdev.meshdi.moduleit.api.LeakDetectorResult;
import me.bottdev.meshdi.moduleit.api.ModuleBatchResult;
import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleClassLoader;
import me.bottdev.meshdi.moduleit.api.ModuleClassLoaderLeakDetector;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleLibraryLoader;
import me.bottdev.meshdi.moduleit.api.ModuleState;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleResolutionDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStartDiagnostic;
import me.bottdev.meshdi.moduleit.core.repository.LocalModuleRepository;
import me.bottdev.meshdi.moduleit.core.utils.TestModuleJarBuilder;
import me.bottdev.meshdi.moduleit.processor.ModuleMetaProcessor;
import me.bottdev.meshdi.processor.MeshdiMetaProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.API_VERSION;
import static me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.NO_LIBRARIES;
import static me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.realResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/// Real module jars, built by the annotation processors, driven through failure and recovery
/// by a real manager and a real context mesh.
@ExtendWith(MockitoExtension.class)
class ModuleRecoveryIntegrationTest {

    /// The test module's eager bean fails while this system property is set.
    static final String FAIL_SWITCH = "meshdi.test.fail-start";
    /// Set by the test module's bean: the name of the thread context class loader's class during start.
    static final String TCCL_PROBE = "meshdi.test.tccl";

    @TempDir
    Path modulesDir;

    @Mock
    ModuleLibraryLoader libraryLoader;

    @Mock
    ModuleClassLoaderLeakDetector leakDetector;

    final List<Diagnostic> diagnostics = new ArrayList<>();

    ContextMesh contextMesh;
    SimpleModuleManager manager;

    @BeforeEach
    void setUp() {
        lenient().when(libraryLoader.loadAll(any(), any())).thenReturn(completedFuture(NO_LIBRARIES));
        lenient().when(leakDetector.awaitUnloadAsync(any(), any()))
                .thenReturn(completedFuture(new LeakDetectorResult.Freed()));

        contextMesh = new DagContextMesh();
        manager = SimpleModuleManager.builder()
                .dependencyResolver(realResolver())
                .environment(SimpleModuleLoadEnvironment.builder()
                        .apiVersion(API_VERSION)
                        .apiLoader(getClass().getClassLoader())
                        .apiPackages(Set.of("me.bottdev.meshdi", "me.bottdev.kern"))
                        .exportRegistry(new SimpleModuleExportRegistry())
                        .diagnosticSinkFactory(new DiagnosticSinkFactory() {
                            @Override
                            public <D extends Diagnostic> DiagnosticSink<D> create() {
                                return DiagnosticSink.forwarding(diagnostics::add);
                            }
                        })
                        .build())
                .libraryLoader(libraryLoader)
                .contextMesh(contextMesh)
                .leakDetector(leakDetector)
                .build();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(FAIL_SWITCH);
        System.clearProperty(TCCL_PROBE);
    }

    private void buildModule(String id, String packageName, String moduleAttributes) throws Exception {
        TestModuleJarBuilder.withProcessors(new MeshdiMetaProcessor(), new ModuleMetaProcessor())
                .withSource(packageName + ".Main", """
                        package %s;

                        import me.bottdev.meshdi.api.InitializationStrategy;
                        import me.bottdev.meshdi.api.annotations.Component;
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;

                        @Component(init = InitializationStrategy.EAGER)
                        @Module(id = "%s", version = "1.0.0"%s)
                        public class Main {
                            public Main() {
                                System.setProperty("%s",
                                        Thread.currentThread().getContextClassLoader().getClass().getSimpleName());
                                if (System.getProperty("%s") != null) throw new IllegalStateException("boom");
                            }
                        }
                        """.formatted(packageName, id, moduleAttributes, TCCL_PROBE, FAIL_SWITCH))
                .buildTo(modulesDir, id + ".jar");
    }

    private void install() throws Exception {
        manager.resolve(new LocalModuleRepository(modulesDir));
        manager.prepareAll().join();
        manager.loadAll();
    }

    @Test
    @DisplayName("the processor writes a data descriptor that resolution reads back without module code")
    void shouldResolveModuleFromGeneratedDescriptor() throws Exception {
        // Given
        buildModule("alpha", "alpha", """
                ,
                        apiVersion = ">=1.0.0",
                        exports = { "alpha.api" },
                        libraries = { @me.bottdev.meshdi.moduleit.api.annotations.Library("org.example:lib:1.0") },
                        repositories = { @me.bottdev.meshdi.moduleit.api.annotations.Repository(
                                id = "company", url = "https://repo.example.com/releases") }
                """);

        // When
        ModuleBatchResult<ModuleCandidate> result = manager.resolve(new LocalModuleRepository(modulesDir));

        // Then
        assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
        ModuleDescriptor descriptor = manager.getHandle("alpha").descriptor();
        assertThat(descriptor.version()).isEqualTo("1.0.0");
        assertThat(descriptor.exports()).containsExactly("alpha.api");
        assertThat(descriptor.libraries()).extracting(library -> library.coordinate())
                .containsExactly("org.example:lib:1.0");
        assertThat(descriptor.repositories()).extracting(repository -> repository.url())
                .containsExactly("https://repo.example.com/releases");
        // Resolution alone must not run the module: its bean would have set the probe.
        assertThat(System.getProperty(TCCL_PROBE)).isNull();
    }

    @Test
    @DisplayName("one jar with an unreadable descriptor is rejected without hiding the other modules")
    void shouldRejectOnlyTheBrokenCandidate() throws Exception {
        // Given
        buildModule("alpha", "alpha", "");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(modulesDir.resolve("broken.jar")))) {
            jar.putNextEntry(new JarEntry(ModuleDescriptor.DESCRIPTOR_FILE));
            jar.write("com.example.LegacyDescriptor".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }

        // When
        ModuleBatchResult<ModuleCandidate> result = manager.resolve(new LocalModuleRepository(modulesDir));

        // Then
        assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
        assertThat(result.processed()).extracting(candidate -> candidate.descriptor().id()).containsExactly("alpha");
        assertThat(result.failed()).extracting(ModuleCandidate::sourceKey).containsExactly("broken.jar");
        assertThat(diagnostics).anyMatch(diagnostic ->
                diagnostic instanceof ModuleResolutionDiagnostic.InvalidDescriptor invalid
                        && invalid.sourceKey().equals("broken.jar"));
    }

    @Test
    @DisplayName("a failed start is rolled back: the module can be started again")
    void shouldRetryStart_afterFailedStart() throws Exception {
        // Given
        buildModule("alpha", "alpha", "");
        install();
        ModuleHandle alpha = manager.getHandle("alpha");
        System.setProperty(FAIL_SWITCH, "true");

        // When
        ModuleBatchResult<ModuleHandle> failed = manager.startAll();

        // Then: nothing of the failed start is left behind
        assertThat(failed).isInstanceOf(ModuleBatchResult.Failed.class);
        assertThat(alpha.state()).isEqualTo(ModuleState.START_FAILED);
        assertThat(alpha.context()).isNull();
        assertThat(contextMesh.contains("alpha")).isFalse();
        assertThat(diagnostics).hasAtLeastOneElementOfType(ModuleStartDiagnostic.ContextNotStarted.class);
        assertThat(diagnostics).noneMatch(ModuleStartDiagnostic.RollbackFailed.class::isInstance);

        // When: the cause is fixed and the start is retried
        System.clearProperty(FAIL_SWITCH);
        ModuleBatchResult<ModuleHandle> retried = manager.startAll();

        // Then
        assertThat(retried).isInstanceOf(ModuleBatchResult.Success.class);
        assertThat(alpha.state()).isEqualTo(ModuleState.STARTED);
        assertThat(contextMesh.contains("alpha")).isTrue();
    }

    @Test
    @DisplayName("a module whose start failed can be unloaded, and its class loader is closed")
    void shouldUnload_afterFailedStart() throws Exception {
        // Given
        buildModule("alpha", "alpha", "");
        install();
        ModuleHandle alpha = manager.getHandle("alpha");
        ModuleClassLoader classLoader = (ModuleClassLoader) alpha.classLoader();
        System.setProperty(FAIL_SWITCH, "true");
        manager.startAll();

        // When
        ModuleBatchResult<ModuleHandle> unloaded = manager.unloadAll().join();

        // Then
        assertThat(unloaded).isInstanceOf(ModuleBatchResult.Success.class);
        assertThat(manager.exists("alpha")).isFalse();
        assertThat(alpha.classLoader()).isNull();
        assertThat(classLoader.isClosed()).isTrue();
    }

    @Test
    @DisplayName("module code runs with the module class loader as thread context class loader")
    void shouldSetContextClassLoader_duringStart() throws Exception {
        // Given
        buildModule("alpha", "alpha", "");
        install();
        ClassLoader before = Thread.currentThread().getContextClassLoader();

        // When
        manager.startAll();

        // Then
        assertThat(System.getProperty(TCCL_PROBE)).isEqualTo(ModuleClassLoader.class.getSimpleName());
        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(before);
    }

    @Test
    @DisplayName("an optional dependency that is not installed does not fail the start")
    void shouldStart_whenOptionalDependencyIsAbsent() throws Exception {
        // Given
        buildModule("alpha", "alpha", """
                ,
                        dependencies = { @me.bottdev.meshdi.moduleit.api.annotations.DependsOn(
                                id = "missing", link = me.bottdev.kern.dependency.DependencyLink.OPTIONAL) }
                """);
        install();

        // When
        ModuleBatchResult<ModuleHandle> started = manager.startAll();

        // Then
        assertThat(started).isInstanceOf(ModuleBatchResult.Success.class);
        assertThat(contextMesh.getVisibleContexts("alpha")).isEmpty();
    }

    @Test
    @DisplayName("a second module exporting an already exported package is not loaded")
    void shouldRejectDuplicateExport() throws Exception {
        // Given
        buildModule("alpha", "alpha", ", exports = { \"shared.api\" }");
        buildModule("beta", "beta", ", exports = { \"shared.api\" }");
        manager.resolve(new LocalModuleRepository(modulesDir));
        manager.prepareAll().join();

        // When
        ModuleBatchResult<ModuleHandle> loaded = manager.loadAll();

        // Then
        assertThat(loaded).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
        assertThat(loaded.processed()).hasSize(1);
        assertThat(loaded.failed()).hasSize(1);
        assertThat(loaded.failed().getFirst().state()).isEqualTo(ModuleState.READY);
        assertThat(loaded.failed().getFirst().classLoader()).isNull();
        assertThat(diagnostics).hasAtLeastOneElementOfType(ModuleLoadDiagnostic.ExportConflict.class);
    }

}
