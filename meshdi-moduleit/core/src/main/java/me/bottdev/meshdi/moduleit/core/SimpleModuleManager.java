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
import me.bottdev.kern.version.VersionRange;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.core.SimpleContextBootstrap;
import me.bottdev.meshdi.core.mesh.DagContextMesh;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleStartException;

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

    private Collection<ModuleCandidate> prepareCandidates(
            List<ModuleCandidate> candidates,
            DiagnosticsBuilder<ModuleDiagnostic> diagnosticsBuilder
    ) {

        Map<String, ModuleCandidate> uniqueCandidates = new HashMap<>();

        for (ModuleCandidate candidate : candidates) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            VersionRange requiredApiVersion = descriptor.apiVersion();

            if (exists(moduleId)) {
                diagnosticsBuilder.append(ModuleDiagnostic.alreadyLoaded(moduleId));
                continue;
            }

            if (uniqueCandidates.containsKey(moduleId)) {
                diagnosticsBuilder.append(ModuleDiagnostic.duplicate(moduleId));
                continue;
            }

            if (!requiredApiVersion.satisfies(loadEnvironment.apiVersion())) {
                diagnosticsBuilder.append(ModuleDiagnostic.apiVersionMismatch(
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

    private DiagnosticResult<ResolutionResult<String, ModuleCandidate>, ModuleDiagnostic> resolveCandidates(
            Collection<ModuleCandidate> uniqueCandidates,
            DiagnosticsBuilder<ModuleDiagnostic> diagnosticsBuilder
    ) {

        DependentContainer<String, ModuleCandidate> container = SimpleDependentContainer.<String, ModuleCandidate>builder()
                .add(uniqueCandidates)
                .build();

        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, DependencyDiagnostic> diagnosticResult =
                dependencyResolver.resolveAndRemember(container);

        if (diagnosticResult.isPresent()) {
            return DiagnosticResult.success(diagnosticResult.unwrap(), diagnosticsBuilder.build());

        } else {
            diagnosticsBuilder.append(ModuleDiagnostic.badResolution(diagnosticResult.unwrapDiagnostics()));
            return DiagnosticResult.failure(diagnosticsBuilder.build());

        }

    }

    @Override
    public Diagnostics<ModuleDiagnostic> load(ModuleRepository repository) throws CandidateListException {

        List<ModuleCandidate> candidates = repository.candidates();

        DiagnosticsBuilder<ModuleDiagnostic> diagnosticsBuilder = ListDiagnostics.builder();

        Collection<ModuleCandidate> uniqueCandidates = prepareCandidates(candidates, diagnosticsBuilder);
        DiagnosticResult<ResolutionResult<String, ModuleCandidate>, ModuleDiagnostic> diagnosticResult =
                resolveCandidates(uniqueCandidates, diagnosticsBuilder);

        Diagnostics<ModuleDiagnostic> diagnostics = diagnosticResult.unwrapDiagnostics();
        if (!diagnosticResult.isPresent()) return diagnostics;

        ResolutionResult<String, ModuleCandidate> resolutionResult = diagnosticResult.unwrap();
        for (ModuleCandidate candidate : resolutionResult.ordered()) {

            ModuleDescriptor descriptor = candidate.descriptor();
            String moduleId = descriptor.id();
            List<String> dependencyIds = descriptor.getVersionedDependencies().stream()
                    .map(VersionedDependencyRequest::key)
                    .toList();
            Set<String> exports = descriptor.exports();

            ClassLoader classLoader = candidate.openClassLoader(loadEnvironment, dependencyIds);
            SimpleModuleHandle handle = new SimpleModuleHandle(candidate, classLoader);

            handles.put(moduleId, handle);
            loadEnvironment.exportRegistry().register(moduleId, exports, classLoader);

        }

        return diagnostics;

    }

    private void handleModuleError(SimpleModuleHandle handle) {
        if (!handle.state().isIntermediate()) return;
        handle.setContext(null);
        handle.setState(ModuleState.FAILED);
    }

    @Override
    public void start(String id) throws ModuleStartException {

        if (!exists(id))
            throw new ModuleStartException("Module \"" + id + "\" does not exist.");

        ModuleHandle handle = getHandle(id);
        ModuleState currentState = handle.state();
        if (currentState != ModuleState.LOADED && currentState != ModuleState.STOPPED)
            throw new ModuleStartException("Required LOADED or STOPPED state to start the module. Actual: " + currentState + ".");

        SimpleModuleHandle simpleHandle = (SimpleModuleHandle) handle;
        simpleHandle.setState(ModuleState.STARTING);

        try {

            ModuleDescriptor descriptor = handle.descriptor();

            Context moduleContext = SimpleContextBootstrap.bootstrap(handle.classLoader())
                    .id(descriptor.id())
                    .build();
            moduleContext.start();

            List<String> sees = descriptor.getVersionedDependencies().stream()
                    .map(VersionedDependencyRequest::key)
                    .toList();

            DagContextMesh.DagMeshRegistration registration = new DagContextMesh.DagMeshRegistration(moduleContext, sees);
            Context meshViewContext = contextMesh.register(registration);

            simpleHandle.setContext(meshViewContext);
            simpleHandle.setState(ModuleState.STARTED);

        } catch (ContextBuildException ex) {
            handleModuleError(simpleHandle);
            throw new ModuleStartException("Failed to bootstrap module context not load.", ex);

        } catch (ContextStartException ex) {
            handleModuleError(simpleHandle);
            throw new ModuleStartException("Failed to start module context.", ex);

        } catch (MeshRegisterException ex) {
            handleModuleError(simpleHandle);
            throw new ModuleStartException("Failed to register module context in a mesh.", ex);

        }

    }

}
