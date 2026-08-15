package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.commons.diagnostic.ListDiagnostics;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.dependency.StatefulDependencyResolver;
import me.bottdev.kern.dependency.graph.GraphStatefulVersionedDependencyResolver;
import me.bottdev.kern.struct.algorithms.cycle.SimpleCycleDetector;
import me.bottdev.kern.struct.algorithms.sort.KahnSorter;
import me.bottdev.kern.struct.paths.CyclePath;
import me.bottdev.kern.version.SemVersionParser;
import me.bottdev.kern.version.VersionRangeParser;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleStartException;
import me.bottdev.meshdi.moduleit.core.repository.LocalModuleRepository;
import me.bottdev.meshdi.moduleit.core.utils.TestModuleCandidate;
import me.bottdev.meshdi.moduleit.core.utils.TestModuleDescriptor;
import me.bottdev.meshdi.moduleit.core.utils.TestModuleJarBuilder;
import me.bottdev.meshdi.moduleit.processor.ModuleMetaProcessor;
import me.bottdev.meshdi.processor.MeshdiMetaProcessor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleModuleManagerTest {

    @TempDir
    private Path repoDir;

    private ModuleRepository repository;
    private ModuleManager manager;

    @BeforeEach
    void setUp() {
        repository = new LocalModuleRepository(repoDir);

        StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver =
                new GraphStatefulVersionedDependencyResolver<>(new KahnSorter(new SimpleCycleDetector()));

        ModuleLoadEnvironment loadEnvironment = new SimpleModuleLoadEnvironment(
                SemVersionParser.parse("1.0.0"),
                getClass().getClassLoader(),
                Set.of("me.bottdev"),
                new SimpleModuleExportRegistry()
        );

        manager = new SimpleModuleManager(dependencyResolver, loadEnvironment);
    }

    @Nested
    class Load {

        @Test
        @DisplayName("load: loads several modules successfully")
        void load_success() throws IOException, CandidateListException {

            TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor())
                    .withSource("com.test.RootModule", """
                        package com.test;
                        
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                        
                        @Module(id = "root", version = "0.0.1", apiVersion = ">=1.0.0")
                        public class RootModule {}
                        """
                    )
                    .buildTo(repoDir, "root-module-0.0.1.jar");

            TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor())
                    .withSource("com.test.HologramModule", """
                        package com.test;
                        
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                        
                        @Module(id = "hologram", version = "0.0.1", apiVersion = ">=1.0.0",
                        dependencies = { @DependsOn(id = "root", version = ">=0.0.1") })
                        public class HologramModule {}
                        """
                    )
                    .buildTo(repoDir, "holograms-module-0.0.1.jar");

            TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor())
                    .withSource("com.test.ToolsModule", """
                        package com.test;
                        
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                        
                        @Module(id = "tools", version = "0.0.1", apiVersion = ">=1.0.0",
                        dependencies = { @DependsOn(id = "root", version = ">=0.0.1") })
                        public class ToolsModule {}
                        """
                    )
                    .buildTo(repoDir, "tools-module-0.0.1.jar");

            manager.load(repository);

            assertThat(manager.getHandles())
                    .hasSize(3)
                    .extracting(ModuleHandle::state)
                    .allMatch(state -> state == ModuleState.LOADED);

        }

        @Test
        @DisplayName("load: exception during listing of candidates")
        void load_candidatesThrow() throws CandidateListException {

            ModuleRepository badRepository = mock(ModuleRepository.class);
            when(badRepository.candidates()).thenThrow(new CandidateListException("Failed to read repository"));

            assertThrows(CandidateListException.class, () -> manager.load(badRepository));

        }

        @Test
        @DisplayName("load: found duplicate module")
        void load_duplicateModule() throws CandidateListException {

            ModuleDescriptor descriptor1 = TestModuleDescriptor.builder("test")
                    .build();

            ModuleDescriptor descriptor2 = TestModuleDescriptor.builder("test")
                    .build();

            ModuleCandidate candidate1 = TestModuleCandidate.of(descriptor1);
            ModuleCandidate candidate2 = TestModuleCandidate.of(descriptor2);

            ModuleRepository badRepository = mock(ModuleRepository.class);
            when(badRepository.candidates()).thenReturn(List.of(candidate1, candidate2));

            Diagnostics<ModuleDiagnostic> diagnostics = manager.load(badRepository);

            assertThat(manager.getHandles())
                    .hasSize(1)
                    .extracting(ModuleHandle::state)
                    .allMatch(state -> state == ModuleState.LOADED);

            assertTrue(diagnostics.has(DiagnosticType.WARN));
            assertThat(diagnostics)
                    .contains(ModuleDiagnostic.duplicate("test"));

        }

        @Test
        @DisplayName("load: module is already loaded")
        void load_moduleAlreadyLoaded() throws CandidateListException {

            ModuleDescriptor descriptor = TestModuleDescriptor.builder("test")
                    .build();

            ModuleCandidate candidate = TestModuleCandidate.of(descriptor);

            ModuleRepository badRepository = mock(ModuleRepository.class);
            when(badRepository.candidates()).thenReturn(List.of(candidate));

            Diagnostics<ModuleDiagnostic> diagnostics1 = manager.load(badRepository);
            Diagnostics<ModuleDiagnostic> diagnostics2 = manager.load(badRepository);

            assertThat(manager.getHandles())
                    .hasSize(1)
                    .extracting(ModuleHandle::state)
                    .allMatch(state -> state == ModuleState.LOADED);

            assertThat(diagnostics1)
                    .hasSize(1);

            assertThat(diagnostics2)
                    .contains(ModuleDiagnostic.alreadyLoaded("test"));

        }

        @Test
        @DisplayName("load: api version is incorrect")
        void load_moduleApiVersionMismatch() throws CandidateListException {

            ModuleDescriptor descriptor = TestModuleDescriptor.builder("test")
                    .apiVersion(">2.0.0")
                    .build();

            ModuleCandidate candidate = TestModuleCandidate.of(descriptor);

            ModuleRepository badRepository = mock(ModuleRepository.class);
            when(badRepository.candidates()).thenReturn(List.of(candidate));

            Diagnostics<ModuleDiagnostic> diagnostics = manager.load(badRepository);

            assertThat(manager.getHandles())
                    .hasSize(0);

            assertThat(diagnostics)
                    .contains(ModuleDiagnostic.apiVersionMismatch(
                            "test",
                            VersionRangeParser.parse(">2.0.0"),
                            SemVersionParser.parse("1.0.0")
                    ));

        }

        @Test
        @DisplayName("load: found circular dependency")
        void load_failedDependencyResolution() throws CandidateListException {

            ModuleDescriptor descriptorA = TestModuleDescriptor.builder("a")
                    .dependsOn("b", "*")
                    .build();

            ModuleDescriptor descriptorB = TestModuleDescriptor.builder("b")
                    .dependsOn("a", "*")
                    .build();

            ModuleCandidate candidateA = TestModuleCandidate.of(descriptorA);
            ModuleCandidate candidateB = TestModuleCandidate.of(descriptorB);

            ModuleRepository badRepository = mock(ModuleRepository.class);
            when(badRepository.candidates()).thenReturn(List.of(candidateA, candidateB));

            Diagnostics<ModuleDiagnostic> diagnostics = manager.load(badRepository);

            assertThat(manager.getHandles())
                    .isEmpty();

            assertTrue(diagnostics.has(DiagnosticType.ERROR));
            assertThat(diagnostics)
                    .contains(ModuleDiagnostic.badResolution(
                            ListDiagnostics.<DependencyDiagnostic>builder()
                                    .append(DependencyDiagnostic.circular(new CyclePath<>("a", List.of("a", "b"))))
                                    .build()
                    ));

        }

        @Test
        @DisplayName("load: module class loader can access exported packages")
        void load_accessExportedPackages() throws IOException, CandidateListException {

            TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor())
                    .withSource("com.root.RootModule", """
                        package com.root;
                       
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                       
                        @Module(id = "root", version = "0.0.1", apiVersion = ">=1.0.0",
                        exports= { "com.root" }
                        )
                         public class RootModule {}
                        """
                    )
                    .withSource("com.root.InternalService", """
                        package com.root;
                        
                        public class InternalService {}
                        """
                    )
                    .buildTo(repoDir, "root-module-0.0.1.jar");

            TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor())
                    .withSource("com.tools.ToolsModule", """
                        package com.tools;
                        
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                        
                        @Module(id = "tools", version = "0.0.1", apiVersion = ">=1.0.0",
                        dependencies = { @DependsOn(id = "root", version = ">=0.0.1") })
                        public class ToolsModule {}
                        """
                    )
                    .buildTo(repoDir, "tools-module-0.0.1.jar");

            manager.load(repository);

            assertThat(manager.getHandles())
                    .hasSize(2)
                    .extracting(ModuleHandle::state)
                    .allMatch(state -> state == ModuleState.LOADED);

            ModuleHandle handle = manager.getHandle("tools");
            ClassLoader classLoader = handle.classLoader();

            assertDoesNotThrow(() -> classLoader.loadClass("com.root.InternalService"));

        }

    }

    @Nested
    class Start {

        @Test
        @DisplayName("start: starts a module and loads its context successfully")
        void start_success() throws IOException, ModuleStartException, CandidateListException {

            TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor(), new MeshdiMetaProcessor())
                    .withSource("com.root.RootModule", """
                        package com.root;
                       
                        import com.root.InternalService;
                        import me.bottdev.meshdi.api.annotations.Component;
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                        
                        @Component
                        @Module(id = "root", version = "0.0.1", apiVersion = ">=1.0.0",
                        exports= { "com.root" }
                        )
                        public class RootModule {
                        
                            private final InternalService internalService;
                        
                            public RootModule(InternalService internalService) {
                                this.internalService = internalService;
                            }
                        
                        }
                        """
                    )
                    .withSource("com.root.InternalService", """
                        package com.root;
                        
                        import me.bottdev.meshdi.api.annotations.Component;
                        
                        @Component
                        public class InternalService {}
                        """
                    )
                    .buildTo(repoDir, "root-module-0.0.1.jar");

            manager.load(repository);
            manager.start("root");

            ModuleHandle handle = manager.getHandle("root");
            assertThat(handle)
                    .satisfies(h ->
                            assertThat(h.state())
                                    .isEqualTo(ModuleState.STARTED)
                    )
                    .satisfies(h ->
                            assertThat(h.context())
                                    .isNotNull()
                                    .extracting(Context::getBindingContainer)
                                    .satisfies(container ->
                                            assertThat(container.size())
                                                    .isEqualTo(2)
                                    )
                    );

        }

        @Test
        @DisplayName("start: module does not exist")
        void start_nonExisting() {

            ModuleStartException ex = assertThrows(ModuleStartException.class, () -> manager.start("root"));
            assertThat(ex)
                    .hasMessageContaining("root")
                    .hasMessageContaining("does not exist");

        }

        @EnumSource(value = ModuleState.class, names = { "STARTING", "STARTED", "FAILED", "RESTARTING", "STOPPING" })
        @ParameterizedTest
        @DisplayName("start: incorrect module start")
        void start_incorrectState(ModuleState state) {

            ModuleHandle handle = mock(ModuleHandle.class);
            when(handle.state()).thenReturn(state);

            ModuleManager spyManager = spy(manager);
            when(spyManager.exists("root")).thenReturn(true);
            when(spyManager.getHandle("root")).thenReturn(handle);

            ModuleStartException ex = assertThrows(ModuleStartException.class, () -> spyManager.start("root"));
            assertThat(ex)
                    .hasMessageContaining("Required LOADED or STOPPED state")
                    .hasMessageContaining("Actual: " + state);

        }

    }

}