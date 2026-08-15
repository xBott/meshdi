package me.bottdev.meshdi.core.mesh;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.exceptions.DisposeException;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegistrationBuildException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterPlanException;
import me.bottdev.meshdi.core.context.MeshViewContext;
import me.bottdev.meshdi.core.mesh.views.KernContextGraphView;
import me.bottdev.meshdi.core.resolvers.MeshBeanResolver;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DagContextMesh implements ContextMesh<DagContextMesh.DagMeshRegistration> {

    public record DagMeshRegistration(
            Context context,
            List<String> sees
    ) implements MeshRegistration {

        @RequiredArgsConstructor
        public static class Builder implements MeshRegistration.Builder {

            private final Context context;
            private final List<String> sees = new ArrayList<>();

            public Builder sees(String id) {
                sees.add(id);
                return this;
            }

            @Override
            public DagMeshRegistration build()
                    throws MeshRegistrationBuildException
            {
                try {
                    Objects.requireNonNull(context, "Context must be non-null.");
                    return new DagMeshRegistration(context, List.copyOf(sees));

                } catch (Exception ex) {
                    throw new MeshRegistrationBuildException("Failed to build a DAG Mesh registration.", ex);
                }
            }

        }

    }

    public static DagMeshRegistration.Builder registration(Context context) {
        return new DagMeshRegistration.Builder(context);
    }

    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final LinkedHashMap<String, DagMeshRegistration> registered = new LinkedHashMap<>();
    private final ContextGraphView graphView = new KernContextGraphView();
    private final Map<String, List<String>> reachableContextsCache = new HashMap<>();

    @Override
    public boolean contains(String id) {
        return registered.containsKey(id);
    }

    @Override
    public Context register(DagMeshRegistration registration)
            throws MeshRegisterException
    {

        Context context = registration.context();
        String contextId = context.getId();
        List<String> sees = registration.sees();

        if (contains(contextId))
            throw new MeshRegisterException("Context \"" + contextId + "\" already exists in a mesh.");

        for (String targetId : sees) {
            if (!contains(targetId))
                throw new MeshRegisterException("Context \"" + targetId + "\" does not exist in a mesh.");
            if (getTransitiveContexts(targetId).contains(contextId))
                throw new MeshRegisterException("Edge " + contextId + " -> " + targetId + " would create a cycle.");
        }

        try {

            registered.put(contextId, registration);

            graphView.addNode(contextId);
            for (String targetId : sees) {
                graphView.addEdge(contextId, targetId);
            }

            invalidateCache();

            return createViewContext(context);

        } catch (Exception ex) {
            throw new MeshRegisterException("Failed to submit registration of context.", ex);

        }

    }

    private Context createViewContext(Context delegate) {
        MeshBeanResolver resolver = new MeshBeanResolver(
                delegate.getId(),
                delegate.getBindingContainer(),
                delegate.getLifecycleManager(),
                this
        );
        return new MeshViewContext(delegate, resolver);
    }

    @Override
    public MeshUnregisterCommand planUnregister(String id, MeshUnregisterStrategy strategy)
            throws MeshUnregisterPlanException
    {
        if (!contains(id))
            throw new MeshUnregisterPlanException("Context \"" + id + "\" does not exist in a mesh.");

        return strategy.createCommand(this, id, contextId -> {
            graphView.removeNode(contextId);
            registered.remove(contextId);
            invalidateCache();
        });
    }

    @Override
    public DagMeshRegistration get(String id) {
        return registered.get(id);
    }

    @Override
    public Optional<DagMeshRegistration> find(String id) {
        return Optional.ofNullable(registered.get(id));
    }

    @Override
    public Set<String> getVisibleContexts(String id) {
        if (!contains(id))
            throw new IllegalArgumentException("Context \"" + id + "\" does not exist in a mesh.");
        return graphView.successors(id);
    }

    @Override
    public Set<String> getDependingContexts(String id) {
        if (!contains(id))
            throw new IllegalArgumentException("Context \"" + id + "\" does not exist in a mesh.");
        return graphView.predecessors(id);
    }

    @Override
    public List<String> getTransitiveContexts(String fromId) {
        if (!contains(fromId))
            throw new IllegalArgumentException("Context \"" + fromId + "\" does not exist in a mesh.");
        return reachableContextsCache.computeIfAbsent(fromId, graphView::bfs);
    }

    private void invalidateCache() {
        reachableContextsCache.clear();
    }

    @Override
    public <T> boolean canLookup(String fromId, TypedKey<T> key) {

        if (!contains(fromId))
            throw new IllegalArgumentException("Context \"" + fromId + "\" is not registered in the mesh.");

        List<String> transitive = getTransitiveContexts(fromId);

        for (String string : transitive) {
            DagMeshRegistration reg = registered.get(string);
            if (reg != null && reg.context().getResolver().contains(key)) {
                return true;
            }
        }

        return false;

    }

    @Override
    public <T> Optional<ContextMeshLookup<T>> lookup(String fromId, TypedKey<T> key) {

        if (!contains(fromId))
            throw new IllegalArgumentException("Context \"" + fromId + "\" is not registered in the mesh.");

        List<String> transitive = getTransitiveContexts(fromId);

        for (String string : transitive) {
            DagMeshRegistration registration = registered.get(string);
            if (registration == null) continue;

            Context context = registration.context();

            if (!context.getBindingContainer().containsBinding(key)) continue;

            ContextMeshLookup<T> lookup = new ContextMeshLookup<>(
                    context,
                    context.getBindingContainer().getBinding(key)
            );

            return Optional.of(lookup);
        }

        return Optional.empty();
    }

    @Override
    public void dispose() throws DisposeException {
        if (disposed.compareAndSet(false, true)) {

            registered.reversed().forEach((_, registration) ->
                    registration.context().dispose()
            );

            registered.clear();
            reachableContextsCache.clear();
            graphView.clear();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
