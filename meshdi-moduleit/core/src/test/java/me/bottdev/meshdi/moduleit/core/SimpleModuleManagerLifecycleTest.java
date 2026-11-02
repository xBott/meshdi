package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;
import me.bottdev.meshdi.core.mesh.DagContextMesh;
import me.bottdev.meshdi.moduleit.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// Drives a real manager with a real context mesh through the whole module lifecycle.
@ExtendWith(MockitoExtension.class)
class SimpleModuleManagerLifecycleTest {

    @Mock
    ModuleLibraryLoader libraryLoader;

    @Mock
    ModuleClassLoaderLeakDetector leakDetector;

    final List<Diagnostic> diagnostics = new ArrayList<>();

    ContextMesh contextMesh;
    SimpleModuleManager manager;
    VirtualModuleHandle a;
    VirtualModuleHandle b;

    @BeforeEach
    void setUp() throws Exception {
        when(libraryLoader.loadAll(any(), any())).thenReturn(completedFuture(NO_LIBRARIES));

        contextMesh = new DagContextMesh();
        manager = SimpleModuleManager.builder()
                .dependencyResolver(realResolver())
                .environment(environment(diagnostics))
                .libraryLoader(libraryLoader)
                .contextMesh(contextMesh)
                .leakDetector(leakDetector)
                .build();

        a = new VirtualModuleHandle(descriptor("a"), new SimpleContextBuilder().id("a").build());
        b = new VirtualModuleHandle(descriptor("b", "a"), new SimpleContextBuilder().id("b").build());
    }

    @Test
    @DisplayName("resolve → prepare → load → start → stop → unload reports every affected module")
    void shouldReportAffectedModules_whenRunningFullLifecycle() throws Exception {
        // When: resolve
        ModuleBatchResult<ModuleCandidate> resolution = manager.resolve(new VirtualModuleRepository(b, a));

        // Then
        assertThat(resolution).isInstanceOf(ModuleBatchResult.Success.class);
        assertThat(resolution.processed()).containsExactly(a.candidate(), b.candidate());

        // When: prepare
        ModuleBatchResult<ModuleHandle> preparation = manager.prepareAll().join();

        // Then
        assertThat(preparation.processed()).containsExactly(a, b);
        assertThat(a.state()).isEqualTo(ModuleState.READY);

        // When: load
        ModuleBatchResult<ModuleHandle> loading = manager.loadAll();

        // Then
        assertThat(loading.processed()).containsExactly(a, b);
        assertThat(b.state()).isEqualTo(ModuleState.LOADED);

        // When: start
        ModuleBatchResult<ModuleHandle> starting = manager.startAll();

        // Then
        assertThat(starting).isInstanceOf(ModuleBatchResult.Success.class);
        assertThat(starting.processed()).containsExactly(a, b);
        assertThat(contextMesh.contains("a")).isTrue();
        assertThat(contextMesh.contains("b")).isTrue();

        // When: stop
        ModuleBatchResult<ModuleHandle> stopping = manager.stopAll();

        // Then
        assertThat(stopping).isInstanceOf(ModuleBatchResult.Success.class);
        assertThat(stopping.processed()).containsExactly(b, a);
        assertThat(contextMesh.contains("a")).isFalse();
        assertThat(contextMesh.contains("b")).isFalse();

        // When: unload
        ModuleBatchResult<ModuleHandle> unloading = manager.unloadAll().join();

        // Then
        assertThat(unloading).isInstanceOf(ModuleBatchResult.Success.class);
        assertThat(unloading.processed()).containsExactly(b, a);
        assertThat(manager.getHandles()).isEmpty();
        verifyNoInteractions(leakDetector);
    }

    @Test
    void shouldResolveModulesAgain_whenTheyWereUnloaded() throws Exception {
        // Given
        VirtualModuleRepository repository = new VirtualModuleRepository(a);
        manager.resolve(repository);
        manager.prepareAll().join();
        manager.loadAll();
        manager.unloadAll().join();

        // When
        ModuleBatchResult<ModuleCandidate> result = manager.resolve(repository);

        // Then
        assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
        assertThat(result.processed()).containsExactly(a.candidate());
        assertThat(manager.exists("a")).isTrue();
    }

}
