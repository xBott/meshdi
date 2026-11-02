package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.wrapper.DiagnosticResult;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.dependency.ResolutionResult;
import me.bottdev.kern.dependency.versioned.StatefulVersionedDependencyResolver;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.api.MeshRegistration;
import me.bottdev.meshdi.api.MeshUnregisterCommand;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.diagnostic.*;
import me.bottdev.meshdi.moduleit.api.exceptions.*;
import me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.PlainCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.semver4j.range.RangeListFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static me.bottdev.meshdi.moduleit.core.utils.ModuleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleModuleManagerTest {

    @Mock
    ContextMesh contextMesh;

    @Mock
    ModuleLibraryLoader libraryLoader;

    @Mock
    ModuleClassLoaderLeakDetector leakDetector;

    @Mock
    DependencyModuleSelector dependencySelector;

    @Mock
    DependentModuleSelector dependentSelector;

    final List<Diagnostic> diagnostics = new ArrayList<>();

    SimpleModuleManager manager;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(contextMesh.register(any()))
                .thenAnswer(invocation -> invocation.<MeshRegistration>getArgument(0).context());
        lenient().when(contextMesh.planUnregister(anyString(), any()))
                .thenReturn(new MeshUnregisterCommand(List.of(), _ -> {}));
        lenient().when(libraryLoader.loadAll(any(), any()))
                .thenReturn(completedFuture(NO_LIBRARIES));

        manager = newManager(realResolver());
    }

    private SimpleModuleManager newManager(StatefulVersionedDependencyResolver<String, ModuleCandidate> resolver) {
        return SimpleModuleManager.builder()
                .dependencyResolver(resolver)
                .environment(environment(diagnostics))
                .libraryLoader(libraryLoader)
                .contextMesh(contextMesh)
                .leakDetector(leakDetector)
                .build();
    }

    // Helpers that bring modules into a given state. Diagnostics of the setup steps are discarded.

    private void resolved(VirtualModuleHandle... handles) throws CandidateListException {
        manager.resolve(new VirtualModuleRepository(handles));
        diagnostics.clear();
    }

    private void ready(VirtualModuleHandle... handles) throws CandidateListException {
        resolved(handles);
        manager.prepareAll().join();
        diagnostics.clear();
    }

    private void loaded(VirtualModuleHandle... handles) throws CandidateListException {
        ready(handles);
        manager.loadAll();
        diagnostics.clear();
    }

    private void started(VirtualModuleHandle... handles) throws CandidateListException {
        loaded(handles);
        manager.startAll();
        diagnostics.clear();
    }

    private ModuleHandle resolvedPlain(String id) throws CandidateListException {
        ModuleCandidate candidate = new PlainCandidate(descriptor(id));
        manager.resolve(() -> List.of(candidate));
        diagnostics.clear();
        return manager.getHandle(id);
    }

    @Nested
    class Resolve {

        @Test
        void shouldReturnUnchanged_whenRepositoryIsEmpty() throws Exception {
            // When
            ModuleBatchResult<ModuleCandidate> result = manager.resolve(new VirtualModuleRepository());

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
            assertThat(manager.getHandles()).isEmpty();
            assertThat(diagnostics).contains(new ModuleResolutionDiagnostic.NothingResolved());
        }

        @Test
        void shouldResolveInDependencyOrder_whenAllCandidatesAreValid() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b", "a");

            // When
            ModuleBatchResult<ModuleCandidate> result = manager.resolve(new VirtualModuleRepository(b, a));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(a.candidate(), b.candidate());
            assertThat(manager.getHandles()).containsExactly(a, b);
            assertThat(manager.getHandles()).allMatch(handle -> handle.state() == ModuleState.RESOLVED);
        }

        @Test
        void shouldWrapCandidateIntoSimpleHandle_whenCandidateIsNotInternal() throws Exception {
            // Given
            ModuleCandidate candidate = new PlainCandidate(descriptor("a"));

            // When
            ModuleBatchResult<ModuleCandidate> result = manager.resolve(() -> List.of(candidate));

            // Then
            assertThat(result.processed()).containsExactly(candidate);
            assertThat(manager.getHandle("a"))
                    .isInstanceOf(SimpleModuleHandle.class)
                    .satisfies(handle -> {
                        assertThat(handle.candidate()).isSameAs(candidate);
                        assertThat(handle.state()).isEqualTo(ModuleState.RESOLVED);
                    });
        }

        @Test
        @DisplayName("Candidates of already existing modules are skipped, not failed")
        void shouldReturnUnchanged_whenModuleAlreadyExists() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            resolved(a);

            // When
            ModuleBatchResult<ModuleCandidate> result = manager.resolve(new VirtualModuleRepository(a));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
            assertThat(diagnostics).contains(new ModuleResolutionDiagnostic.AlreadyLoaded("a"));
        }

        @Test
        void shouldRejectSecondCandidate_whenIdIsDuplicated() throws Exception {
            // Given
            VirtualModuleHandle first = virtual("a");
            VirtualModuleHandle duplicate = virtual("a");

            // When
            ModuleBatchResult<ModuleCandidate> result = manager.resolve(new VirtualModuleRepository(first, duplicate));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
            assertThat(result.processed()).containsExactly(first.candidate());
            assertThat(result.failed()).containsExactly(duplicate.candidate());
            assertThat(manager.getHandle("a")).isSameAs(first);
            assertThat(diagnostics).contains(new ModuleResolutionDiagnostic.Duplicate("a"));
        }

        @Test
        void shouldRejectCandidate_whenApiVersionIsNotSatisfied() throws Exception {
            // Given
            VirtualModuleHandle incompatible = new VirtualModuleHandle(
                    VirtualModuleDescriptor.builder()
                            .id("incompatible")
                            .semver(MODULE_VERSION)
                            .apiVersion(RangeListFactory.create(">=2.0.0"))
                            .build(),
                    mockContext("incompatible")
            );
            VirtualModuleHandle compatible = virtual("compatible");

            // When
            ModuleBatchResult<ModuleCandidate> result =
                    manager.resolve(new VirtualModuleRepository(incompatible, compatible));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
            assertThat(result.processed()).containsExactly(compatible.candidate());
            assertThat(result.failed()).containsExactly(incompatible.candidate());
            assertThat(manager.exists("incompatible")).isFalse();
            assertThat(diagnostics).hasAtLeastOneElementOfType(ModuleResolutionDiagnostic.ApiVersionMismatch.class);
        }

        @Test
        void shouldReturnFailed_whenEveryCandidateIsRejected() throws Exception {
            // Given
            VirtualModuleHandle incompatible = new VirtualModuleHandle(
                    VirtualModuleDescriptor.builder()
                            .id("incompatible")
                            .semver(MODULE_VERSION)
                            .apiVersion(RangeListFactory.create(">=2.0.0"))
                            .build(),
                    mockContext("incompatible")
            );

            // When
            ModuleBatchResult<ModuleCandidate> result = manager.resolve(new VirtualModuleRepository(incompatible));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactly(incompatible.candidate());
        }

        @Test
        @DisplayName("Missing dependency fails every unique candidate together with already rejected ones")
        void shouldFailAllCandidates_whenDependencyIsMissing() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle duplicate = virtual("a");
            VirtualModuleHandle b = virtual("b", "missing");

            // When
            ModuleBatchResult<ModuleCandidate> result = manager.resolve(new VirtualModuleRepository(a, duplicate, b));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed())
                    .containsExactlyInAnyOrder(a.candidate(), duplicate.candidate(), b.candidate());
            assertThat(manager.getHandles()).isEmpty();
            assertThat(diagnostics).hasAtLeastOneElementOfType(ModuleResolutionDiagnostic.BadDependencyResolution.class);
        }

        @Test
        void shouldFailAllCandidates_whenDependenciesFormCycle() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a", "b");
            VirtualModuleHandle b = virtual("b", "a");

            // When
            ModuleBatchResult<ModuleCandidate> result = manager.resolve(new VirtualModuleRepository(a, b));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactlyInAnyOrder(a.candidate(), b.candidate());
            assertThat(manager.getHandles()).isEmpty();
        }

        @Test
        void shouldFailCandidates_whenResolverDropsThem() throws Exception {
            // Given
            @SuppressWarnings("unchecked")
            StatefulVersionedDependencyResolver<String, ModuleCandidate> resolver =
                    mock(StatefulVersionedDependencyResolver.class);
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b");
            ResolutionResult<String, ModuleCandidate> partial =
                    new ResolutionResult<>(List.of(a.candidate()), List.of(List.of(a.candidate())));
            when(resolver.resolveAndRemember(any()))
                    .thenReturn(DiagnosticResult.<ResolutionResult<String, ModuleCandidate>, DependencyDiagnostic>success(partial));
            SimpleModuleManager managerWithResolver = newManager(resolver);

            // When
            ModuleBatchResult<ModuleCandidate> result = managerWithResolver.resolve(new VirtualModuleRepository(a, b));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
            assertThat(result.processed()).containsExactly(a.candidate());
            assertThat(result.failed()).containsExactly(b.candidate());
            assertThat(managerWithResolver.getHandles()).containsExactly(a);
        }

        @Test
        void shouldPropagateException_whenRepositoryCannotListCandidates() throws Exception {
            // Given
            ModuleRepository repository = mock(ModuleRepository.class);
            when(repository.candidates()).thenThrow(new CandidateListException("boom"));

            // When / Then
            assertThatThrownBy(() -> manager.resolve(repository))
                    .isInstanceOf(CandidateListException.class)
                    .hasMessage("boom");
        }

    }

    @Nested
    class Prepare {

        @Test
        void shouldReturnUnchanged_whenNoModuleIsResolved() {
            // When
            ModuleBatchResult<ModuleHandle> result = manager.prepareAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
            verifyNoInteractions(libraryLoader);
        }

        @Test
        void shouldMarkModulesReady_whenLibrariesAreLoaded() throws Exception {
            // Given
            ModuleHandle handle = resolvedPlain("a");
            Path library = Path.of("isolated.jar");
            when(libraryLoader.loadAll(eq(List.of(handle)), any()))
                    .thenReturn(completedFuture(new ModuleLibrariesResult(
                            List.of(),
                            Map.of(handle, List.of(library)),
                            Set.of()
                    )));

            // When
            ModuleBatchResult<ModuleHandle> result = manager.prepareAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(handle);
            assertThat(handle.state()).isEqualTo(ModuleState.READY);
            assertThat(handle.libraries()).containsExactly(library);
        }

        @Test
        void shouldReturnFailed_whenLibrariesOfAllModulesFail() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b");
            resolved(a, b);
            when(libraryLoader.loadAll(any(), any()))
                    .thenReturn(completedFuture(new ModuleLibrariesResult(List.of(), Map.of(), Set.of(a, b))));

            // When
            ModuleBatchResult<ModuleHandle> result = manager.prepareAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactlyInAnyOrder(a, b);
            assertThat(a.state()).isEqualTo(ModuleState.RESOLVED);
            assertThat(b.state()).isEqualTo(ModuleState.RESOLVED);
        }

        @Test
        @DisplayName("Only modules whose libraries failed stay RESOLVED; the rest of the batch is prepared")
        void shouldReturnPartialSuccess_whenLibrariesOfSomeModulesFail() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b");
            resolved(a, b);
            when(libraryLoader.loadAll(any(), any()))
                    .thenReturn(completedFuture(new ModuleLibrariesResult(List.of(), Map.of(), Set.of(a))));

            // When
            ModuleBatchResult<ModuleHandle> result = manager.prepareAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
            assertThat(result.processed()).containsExactly(b);
            assertThat(result.failed()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.RESOLVED);
            assertThat(b.state()).isEqualTo(ModuleState.READY);
        }

        @Test
        void shouldPrepareModuleAgain_afterItsLibrariesFailed() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            resolved(a);
            when(libraryLoader.loadAll(any(), any()))
                    .thenReturn(completedFuture(new ModuleLibrariesResult(List.of(), Map.of(), Set.of(a))))
                    .thenReturn(completedFuture(NO_LIBRARIES));
            manager.prepareAll().join();

            // When
            ModuleBatchResult<ModuleHandle> retry = manager.prepareAll().join();

            // Then
            assertThat(retry.processed()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.READY);
        }

        @Test
        @DisplayName("Modules that are not RESOLVED are left out of both lists")
        void shouldPrepareOnlyResolvedModules_whenSomeAreAlreadyReady() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b");
            ready(a);
            resolved(b);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.prepareAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(b);
            verify(libraryLoader).loadAll(eq(List.of(b)), any());
        }

    }

    @Nested
    class Load {

        @Test
        void shouldReturnUnchanged_whenThereAreNoModules() {
            // When
            ModuleBatchResult<ModuleHandle> result = manager.loadAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
        }

        @Test
        void shouldLoadAllModules_whenAllAreReady() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b", "a");
            ready(a, b);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.loadAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(a, b);
            assertThat(a.state()).isEqualTo(ModuleState.LOADED);
            assertThat(b.state()).isEqualTo(ModuleState.LOADED);
            assertThat(diagnostics).contains(new ModuleLoadDiagnostic.LoadedN(2));
        }

        @Test
        void shouldReportNotReadyModulesAsFailed_whenStatesAreMixed() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b");
            ready(a);
            resolved(b);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.loadAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
            assertThat(result.processed()).containsExactly(a);
            assertThat(result.failed()).containsExactly(b);
            assertThat(b.state()).isEqualTo(ModuleState.RESOLVED);
            assertThat(diagnostics).contains(new ModuleLoadDiagnostic.LoadedN(1));
        }

        @Test
        @DisplayName("Pins current behaviour: modules already in the target state are reported as failed")
        void shouldReportAlreadyLoadedModulesAsFailed() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            loaded(a);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.loadAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.LOADED);
            assertThat(diagnostics).contains(new ModuleLoadDiagnostic.NothingLoaded());
        }

    }

    @Nested
    class Start {

        @Test
        void shouldReturnUnchanged_whenThereAreNoModules() {
            // When
            ModuleBatchResult<ModuleHandle> result = manager.startAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
        }

        @Test
        void shouldStartAllModules_whenAllAreLoaded() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b", "a");
            loaded(a, b);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.startAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(a, b);
            assertThat(a.state()).isEqualTo(ModuleState.STARTED);
            assertThat(b.state()).isEqualTo(ModuleState.STARTED);
            assertThat(diagnostics).contains(new ModuleStartDiagnostic.StartedN(2));
            verify(contextMesh, times(2)).register(any());
        }

        @Test
        void shouldReportModuleAsFailed_whenMeshRegistrationFails() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b");
            loaded(a, b);
            doThrow(new MeshRegisterException("boom"))
                    .when(contextMesh).register(argThat(registration -> registration.context() == b.context()));

            // When
            ModuleBatchResult<ModuleHandle> result = manager.startAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
            assertThat(result.processed()).containsExactly(a);
            assertThat(result.failed()).containsExactly(b);
            assertThat(b.state()).isEqualTo(ModuleState.START_FAILED);
            assertThat(diagnostics).hasAtLeastOneElementOfType(ModuleStartDiagnostic.MeshRegistrationFailed.class);
        }

        @Test
        void shouldReturnFailed_whenModulesAreAlreadyStarted() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            started(a);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.startAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactly(a);
            assertThat(diagnostics).contains(new ModuleStartDiagnostic.NothingStarted());
        }

    }

    @Nested
    class Stop {

        @Test
        void shouldReturnUnchanged_whenThereAreNoModules() {
            // When
            ModuleBatchResult<ModuleHandle> result = manager.stopAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
        }

        @Test
        void shouldStopDependentsFirst_whenAllAreStarted() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b", "a");
            started(a, b);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.stopAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(b, a);
            assertThat(a.state()).isEqualTo(ModuleState.STOPPED);
            assertThat(b.state()).isEqualTo(ModuleState.STOPPED);
            assertThat(diagnostics).contains(new ModuleStopDiagnostic.StoppedN(2));
        }

        @Test
        void shouldReturnFailed_whenModulesAreNotStarted() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            loaded(a);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.stopAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.LOADED);
            assertThat(diagnostics).contains(new ModuleStopDiagnostic.NothingStopped());
        }

        @Test
        void shouldReportModuleAsFailed_whenUnregisterPlanFails() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b", "a");
            started(a, b);
            doThrow(new MeshContextSelectionException("boom"))
                    .when(contextMesh).planUnregister(eq("b"), any());

            // When
            ModuleBatchResult<ModuleHandle> result = manager.stopAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
            assertThat(result.processed()).containsExactly(a);
            assertThat(result.failed()).containsExactly(b);
            assertThat(b.state()).isEqualTo(ModuleState.STOP_FAILED);
            assertThat(diagnostics).hasAtLeastOneElementOfType(ModuleStopDiagnostic.MeshUnregisterPlanFailed.class);
        }

    }

    @Nested
    class Unload {

        @Test
        void shouldReturnUnchanged_whenThereAreNoModules() {
            // When
            ModuleBatchResult<ModuleHandle> result = manager.unloadAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
        }

        @Test
        void shouldUnloadAndForgetModules_whenAllAreLoaded() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b", "a");
            loaded(a, b);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.unloadAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(b, a);
            assertThat(manager.getHandles()).isEmpty();
            assertThat(diagnostics).contains(new ModuleUnloadDiagnostic.UnloadedN(2));
        }

        @Test
        @DisplayName("Persistent modules are skipped: they are neither processed nor failed")
        void shouldSkipPersistentModules() throws Exception {
            // Given
            VirtualModuleHandle kept = persistent("kept");
            VirtualModuleHandle a = virtual("a");
            loaded(kept, a);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.unloadAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(a);
            assertThat(result.failed()).isEmpty();
            assertThat(manager.exists("kept")).isTrue();
            assertThat(diagnostics).contains(new ModuleUnloadDiagnostic.SkippedPersistent("kept"));
        }

        @Test
        void shouldReturnUnchanged_whenOnlyPersistentModulesExist() throws Exception {
            // Given
            loaded(persistent("kept"));

            // When
            ModuleBatchResult<ModuleHandle> result = manager.unloadAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
            assertThat(diagnostics).contains(new ModuleUnloadDiagnostic.NothingUnloaded());
        }

        @Test
        @DisplayName("Regression: SimpleModuleHandle signals failed unload with a null result, which used to throw NPE")
        void shouldReportSimpleHandleAsFailed_whenItIsInIncorrectState() throws Exception {
            // Given
            ModuleHandle handle = resolvedPlain("a");

            // When
            CompletableFuture<ModuleBatchResult<ModuleHandle>> future = manager.unloadAll();

            // Then
            assertThat(future).succeedsWithin(Duration.ofSeconds(1))
                    .satisfies(result -> {
                        assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
                        assertThat(result.failed()).containsExactly(handle);
                    });
            assertThat(manager.exists("a")).isTrue();
        }

        @Test
        void shouldReportVirtualHandleAsFailed_whenItIsInIncorrectState() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            resolved(a);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.unloadAll().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactly(a);
            assertThat(manager.exists("a")).isTrue();
            assertThat(diagnostics).contains(new ModuleUnloadDiagnostic.IncorrectState("a", ModuleState.RESOLVED));
        }

        @Test
        void shouldReportModuleAsFailed_whenItStillHasDependents() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b", "a");
            loaded(a, b);
            when(dependentSelector.selectDependents("a", manager)).thenReturn(List.of(a));

            // When
            ModuleBatchResult<ModuleHandle> result = manager.unload("a", dependentSelector).confirm().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.UNLOAD_FAILED);
            assertThat(manager.exists("a")).isTrue();
            assertThat(diagnostics).hasAtLeastOneElementOfType(ModuleUnloadDiagnostic.ForgetFailed.class);
        }

        @Test
        @DisplayName("Result completes only after the leak check; a leaked module is still unloaded")
        void shouldWaitForLeakCheck_whenClassLoaderIsTracked() throws Exception {
            // Given
            VirtualModuleHandle a = VirtualModuleHandle.builder()
                    .descriptor(descriptor("a"))
                    .context(mockContext("a"))
                    .trackClassLoaderOnUnload(true)
                    .build();
            loaded(a);
            CompletableFuture<LeakDetectorResult> leakCheck = new CompletableFuture<>();
            when(leakDetector.awaitUnloadAsync(eq("a"), any())).thenReturn(leakCheck);

            // When
            CompletableFuture<ModuleBatchResult<ModuleHandle>> future = manager.unloadAll();

            // Then
            assertThat(future).isNotDone();

            // When
            leakCheck.complete(new LeakDetectorResult.Leaked(new IllegalStateException("still referenced")));

            // Then
            ModuleBatchResult<ModuleHandle> result = future.join();
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(a);
            assertThat(diagnostics).hasAtLeastOneElementOfType(ModuleUnloadDiagnostic.Leaked.class);
            verify(leakDetector).track(eq("a"), any());
        }

    }

    @Nested
    class Restart {

        @Test
        void shouldReturnUnchanged_whenThereAreNoModules() {
            // When
            ModuleBatchResult<ModuleHandle> result = manager.restartAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
        }

        @Test
        void shouldStopAndStartModules_whenAllAreStarted() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b", "a");
            started(a, b);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.restartAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(a, b);
            assertThat(a.state()).isEqualTo(ModuleState.STARTED);
            assertThat(b.state()).isEqualTo(ModuleState.STARTED);
            verify(contextMesh, times(2)).planUnregister(anyString(), any());
        }

        @Test
        void shouldStartModule_whenItWasNotStarted() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            loaded(a);

            // When
            ModuleBatchResult<ModuleHandle> result = manager.restartAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.STARTED);
        }

        @Test
        @DisplayName("Result reflects the start phase: a module that failed to stop cannot be started again")
        void shouldReportModuleAsFailed_whenItCannotBeStopped() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            started(a);
            doThrow(new MeshContextSelectionException("boom"))
                    .when(contextMesh).planUnregister(eq("a"), any());

            // When
            ModuleBatchResult<ModuleHandle> result = manager.restartAll();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.failed()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.STOP_FAILED);
        }

    }

    @Nested
    class Commands {

        @FunctionalInterface
        interface CommandFactory {
            ModuleBatchCommand<?> create(
                    SimpleModuleManager manager,
                    DependencyModuleSelector dependencySelector,
                    DependentModuleSelector dependentSelector,
                    String id
            ) throws Exception;
        }

        static Stream<Arguments> commands() {
            return Stream.of(
                    Arguments.of("prepare", (CommandFactory) (m, dep, _, id) -> m.prepare(id, dep), ModulePrepareException.class),
                    Arguments.of("load", (CommandFactory) (m, dep, _, id) -> m.load(id, dep), ModuleLoadException.class),
                    Arguments.of("start", (CommandFactory) (m, dep, _, id) -> m.start(id, dep), ModuleStartException.class),
                    Arguments.of("stop", (CommandFactory) (m, _, dpt, id) -> m.stop(id, dpt), ModuleStopException.class),
                    Arguments.of("unload", (CommandFactory) (m, _, dpt, id) -> m.unload(id, dpt), ModuleUnloadException.class),
                    Arguments.of("restart", (CommandFactory) (m, _, dpt, id) -> m.restart(id, dpt), ModuleRestartException.class)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("commands")
        void shouldThrowIllegalArgument_whenModuleDoesNotExist(
                String name,
                CommandFactory factory,
                Class<? extends Exception> ignored
        ) {
            // When / Then
            assertThatThrownBy(() -> factory.create(manager, dependencySelector, dependentSelector, "missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("commands")
        void shouldWrapSelectionException_whenSelectorFails(
                String name,
                CommandFactory factory,
                Class<? extends Exception> expected
        ) throws Exception {
            // Given
            resolved(virtual("a"));
            ModuleSelectionException cause = new ModuleSelectionException("boom");
            lenient().when(dependencySelector.selectDependencies("a", manager)).thenThrow(cause);
            lenient().when(dependentSelector.selectDependents("a", manager)).thenThrow(cause);

            // When / Then
            assertThatThrownBy(() -> factory.create(manager, dependencySelector, dependentSelector, "a"))
                    .isInstanceOf(expected)
                    .hasCause(cause);
        }

        @Test
        void shouldNotExecute_untilConfirmed() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            ready(a);
            when(dependencySelector.selectDependencies("a", manager)).thenReturn(List.of(a));

            // When
            ModuleBatchCommand<ModuleBatchResult<ModuleHandle>> command = manager.load("a", dependencySelector);

            // Then
            assertThat(command.handles()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.READY);

            // When
            ModuleBatchResult<ModuleHandle> result = command.confirm();

            // Then
            assertThat(result.processed()).containsExactly(a);
            assertThat(a.state()).isEqualTo(ModuleState.LOADED);
        }

        @Test
        void shouldProcessOnlySelectedModules_whenConfirmed() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            VirtualModuleHandle b = virtual("b");
            resolved(a, b);
            when(dependencySelector.selectDependencies("a", manager)).thenReturn(List.of(a));

            // When
            ModuleBatchResult<ModuleHandle> result = manager.prepare("a", dependencySelector).confirm().join();

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.processed()).containsExactly(a);
            assertThat(b.state()).isEqualTo(ModuleState.RESOLVED);
        }

        @Test
        void shouldExposeUnmodifiableHandles() throws Exception {
            // Given
            VirtualModuleHandle a = virtual("a");
            resolved(a);
            when(dependencySelector.selectDependencies("a", manager)).thenReturn(new ArrayList<>(List.of(a)));

            // When
            ModuleBatchCommand<ModuleBatchResult<ModuleHandle>> command = manager.load("a", dependencySelector);

            // Then
            assertThatThrownBy(() -> command.handles().add(a))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

    }

}
