package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.commons.diagnostic.DiagnosticsBuilder;
import me.bottdev.kern.commons.diagnostic.ListDiagnostics;
import me.bottdev.kern.commons.wrapper.DiagnosticResult;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.dependency.DependentContainer;
import me.bottdev.kern.dependency.ResolutionResult;
import me.bottdev.kern.dependency.StatefulDependencyResolver;
import me.bottdev.kern.dependency.containers.SimpleDependentContainer;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.VersionRange;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.core.SimpleContextBootstrap;
import me.bottdev.meshdi.core.mesh.DagContextMesh;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleLoadDiagnostic;
import me.bottdev.meshdi.moduleit.api.diagnostic.ModuleStartDiagnostic;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleStartException;
import me.bottdev.meshdi.moduleit.api.exceptions.RequireDependencyException;

import java.util.*;

public class SimpleModuleManager implements ModuleManager {

    private final StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver;
    private final ModuleLoadEnvironment loadEnvironment;
    private final Map<String, SimpleModuleHandle> handles = new LinkedHashMap<>();
    private final DagContextMesh contextMesh = new DagContextMesh();

    public SimpleModuleManager(
            @NonNull StatefulDependencyResolver<String, ModuleCandidate> dependencyResolver,
            @NonNull ModuleLoadEnvironment loadEnvironment
    ) {
        this.dependencyResolver = dependencyResolver;
        this.loadEnvironment = loadEnvironment;
    }


    @Override
    public ModuleLoadEnvironment loadEnvironment() {
        return loadEnvironment;
    }

    @Override
    public List<ModuleHandle> getHandles() {
        return handles.values().stream()
                .map(handle -> (ModuleHandle)handle)
                .toList();
    }

    @Override
    public boolean exists(String id) {
        return handles.containsKey(id);
    }

    @Override
    public ModuleHandle getHandle(String id) {
        return handles.get(id);
    }

    @Override
    public List<ModuleHandle> getDependencyHandles(String id) {
        ModuleHandle handle = getHandle(id);
        if (handle == null) return List.of();
        return handle.descriptor().getVersionedDependencies().stream()
                .map(request -> {
                    String key = request.key();
                    return getHandle(key);
                })
                .toList();
    }

    private Collection<ModuleCandidate> prepareCandidates(
            List<ModuleCandidate> candidates,
            DiagnosticsBuilder<ModuleLoadDiagnostic> diagnosticsBuilder
    ) {

        Map<String, ModuleCandidate> uniqueCandidates = new HashMap<>();

        for (ModuleCandidate candidate : candidates) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            VersionRange requiredApiVersion = descriptor.apiVersion();

            if (exists(moduleId)) {
                diagnosticsBuilder.append(ModuleLoadDiagnostic.alreadyLoaded(moduleId));
                continue;
            }

            if (uniqueCandidates.containsKey(moduleId)) {
                diagnosticsBuilder.append(ModuleLoadDiagnostic.duplicate(moduleId));
                continue;
            }

            if (!requiredApiVersion.satisfies(loadEnvironment.apiVersion())) {
                diagnosticsBuilder.append(ModuleLoadDiagnostic.apiVersionMismatch(
                        moduleId,
                        requiredApiVersion,
                        loadEnvironment.apiVersion())
                );
                continue;
            }

            uniqueCandidates.put(moduleId, candidate);

        }

        return uniqueCandidates.values();
    }

    private DiagnosticResult<ResolutionResult<String, ModuleCandidate>, ModuleLoadDiagnostic> resolveCandidates(
            Collection<ModuleCandidate> uniqueCandidates,
            DiagnosticsBuilder<ModuleLoadDiagnostic> diagnosticsBuilder
    ) {

        DependentContainer<String, ModuleCandidate> container = SimpleDependentContainer.<String, ModuleCandidate>builder()
                .add(uniqueCandidates)
                .build();

        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, DependencyDiagnostic> diagnosticResult =
                dependencyResolver.resolveAndRemember(container);

        if (diagnosticResult.isPresent()) {
            return DiagnosticResult.success(diagnosticResult.unwrap(), diagnosticsBuilder.build());

        } else {
            diagnosticsBuilder.append(ModuleLoadDiagnostic.badResolution(diagnosticResult.unwrapDiagnostics()));
            return DiagnosticResult.failure(diagnosticsBuilder.build());

        }

    }

    @Override
    public Diagnostics<ModuleLoadDiagnostic> load(ModuleRepository repository) throws CandidateListException {

        List<ModuleCandidate> candidates = repository.candidates();

        DiagnosticsBuilder<ModuleLoadDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        Collection<ModuleCandidate> uniqueCandidates = prepareCandidates(candidates, diagnosticsBuilder);
        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, ModuleLoadDiagnostic> diagnosticResult =
                resolveCandidates(uniqueCandidates, diagnosticsBuilder);

        if (!diagnosticResult.isPresent()) {
            return diagnosticResult.unwrapDiagnostics();
        }

        ResolutionResult<String, ModuleCandidate> resolutionResult = diagnosticResult.unwrap();

        for (ModuleCandidate candidate : resolutionResult.ordered()) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            SemVersion version = descriptor.version();
            List<String> dependencyIds = descriptor.getVersionedDependencies().stream()
                    .map(VersionedDependencyRequest::key)
                    .toList();
            Set<String> exports = descriptor.exports();

            ClassLoader classLoader = candidate.openClassLoader(loadEnvironment, dependencyIds);
            SimpleModuleHandle handle = new SimpleModuleHandle(candidate, classLoader);

            handles.put(moduleId, handle);
            loadEnvironment.exportRegistry().register(moduleId, exports, classLoader);

            diagnosticsBuilder.append(ModuleLoadDiagnostic.loaded(moduleId, version));

        }

        return diagnosticsBuilder.build();

    }

    private void handleModuleError(SimpleModuleHandle handle) {
        if (!handle.state().isIntermediate()) return;
        handle.setContext(null);
        handle.setState(ModuleState.FAILED);
    }

    private void start(SimpleModuleHandle handle) throws
            RequireDependencyException,
            IllegalStateException,
            ContextBuildException,
            ContextStartException,
            MeshRegisterException
    {

        ModuleDescriptor descriptor = handle.descriptor();
        List<ModuleHandle> dependencyHandles = getDependencyHandles(descriptor.id());
        boolean dependenciesStarted = dependencyHandles.stream()
                .allMatch(dependency -> dependency.state() == ModuleState.STARTED);

        if (!dependenciesStarted) {
            List<ModuleHandle> notStartedDependencies = dependencyHandles.stream()
                    .filter(dependency -> dependency.state() != ModuleState.STARTED)
                    .toList();

            throw new RequireDependencyException("Not all dependencies have started.", notStartedDependencies);
        }

        ModuleState currentState = handle.state();
        if (currentState != ModuleState.LOADED && currentState != ModuleState.STOPPED)
            throw new IllegalStateException("Required LOADED or STOPPED state to start the module. Actual: " + currentState + ".");

        handle.setState(ModuleState.STARTING);


        Context moduleContext = SimpleContextBootstrap.bootstrap(handle.classLoader())
                .id(descriptor.id())
                .build();

        List<String> sees = descriptor.getVersionedDependencies().stream()
                .map(VersionedDependencyRequest::key)
                .toList();

        DagContextMesh.DagMeshRegistration registration = new DagContextMesh.DagMeshRegistration(moduleContext, sees);
        Context meshViewContext = contextMesh.register(registration);
        meshViewContext.start();

        handle.setContext(meshViewContext);
        handle.setState(ModuleState.STARTED);

    }

    @Override
    public void start(String id) throws ModuleStartException {
        if (!exists(id))
            throw new ModuleStartException("Module \"" + id + "\" does not exist.");

        ModuleHandle handle = getHandle(id);
        SimpleModuleHandle simpleHandle = (SimpleModuleHandle) handle;

        boolean success = false;
        try {
            start(simpleHandle);
            success = true;

        } catch (RequireDependencyException ex) {
            throw new ModuleStartException("Module requires all its dependencies to be started.", ex);

        } catch (IllegalStateException ex) {
            throw new ModuleStartException("Starting module from incorrect state.", ex);

        } catch (ContextBuildException ex) {
            throw new ModuleStartException("Failed to bootstrap module context from class loader.", ex);

        } catch (ContextStartException ex) {
            throw new ModuleStartException("Failed to start module context.", ex);

        } catch (MeshRegisterException ex) {
            throw new ModuleStartException("Failed to register module in a context mesh.", ex);

        } finally {
            if (!success) handleModuleError(simpleHandle);

        }

    }

    @Override
    public Diagnostics<ModuleStartDiagnostic> startAll() {

        DiagnosticsBuilder<ModuleStartDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();


        int started = 0;

        for (ModuleHandle handle : getHandles()) {

            ModuleDescriptor descriptor = handle.descriptor();
            String moduleId = descriptor.id();
            SemVersion version = descriptor.version();

            SimpleModuleHandle simpleHandle = (SimpleModuleHandle) handle;

            ModuleState state = handle.state();
            if (state != ModuleState.LOADED && state != ModuleState.STOPPED) continue;

            boolean success = false;
            try {
                start(simpleHandle);
                success = true;
                started++;

                diagnosticsBuilder.append(ModuleStartDiagnostic.started(moduleId, version));

            } catch (RequireDependencyException ex) {
                diagnosticsBuilder.append(ModuleStartDiagnostic.requireDependencies(moduleId, ex.getDependencyHandles()));

            } catch (ContextStartException ex) {
                diagnosticsBuilder.append(ModuleStartDiagnostic.bootstrapFailed(moduleId));

            } catch (MeshRegisterException ex) {
                diagnosticsBuilder.append(ModuleStartDiagnostic.contextNotStarted(moduleId));

            } catch (ContextBuildException ex) {
                diagnosticsBuilder.append(ModuleStartDiagnostic.meshRegistrationFailed(moduleId));

            } finally {
                if (!success) handleModuleError(simpleHandle);

            }

        }

        if (started > 0) {
            diagnosticsBuilder.append(ModuleStartDiagnostic.startedN(started));

        } else {
            diagnosticsBuilder.append(ModuleStartDiagnostic.nothingStarted());

        }

        return diagnosticsBuilder.build();
        
    }

}
