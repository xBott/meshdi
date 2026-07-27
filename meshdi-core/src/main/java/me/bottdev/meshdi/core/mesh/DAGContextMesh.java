package me.bottdev.meshdi.core.mesh;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.exceptions.DisposeException;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterPlanException;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DAGContextMesh implements ContextMesh {

    @RequiredArgsConstructor
    public class DAGRegistration implements ContextMesh.Registration {

        private final Context context;
        private final List<String> sees = new ArrayList<>();

        public DAGRegistration sees(String id) {
            sees.add(id);
            return this;
        }

        @Override
        public DAGContextMesh submit()
                throws MeshRegisterException
        {

            String contextId = context.getId();

            for (String targetId : sees) {
                if (!contains(targetId))
                    throw new MeshRegisterException("Context \"" + targetId + "\" does not exist in a mesh.");
                if (getTransitiveContexts(targetId).contains(contextId))
                    throw new MeshRegisterException("Edge " + contextId + " -> " + targetId + " would create a cycle.");
            }

            try {

                registered.put(contextId, context);
                registrationOrder.add(contextId);

                graphView.addNode(contextId);
                for (String targetId : sees) {
                    graphView.addEdge(contextId, targetId);
                }

                invalidateCache();

                return DAGContextMesh.this;

            } catch (Exception ex) {
                throw new MeshRegisterException("Failed to submit registration of context.", ex);

            }

        }
    }

    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final Map<String, Context> registered = new HashMap<>();
    private final List<String> registrationOrder = new ArrayList<>();
    private final ContextGraphView graphView = new SimpleContextGraphView();
    private final Map<String, List<String>> reachableContextsCache = new HashMap<>();

    @Override
    public boolean contains(String id) {
        return registered.containsKey(id);
    }

    @Override
    public DAGRegistration register(Context context)
            throws MeshRegisterException
    {

        String contextId = context.getId();
        if (contains(contextId))
            throw new MeshRegisterException("Context \"" + contextId + "\" already exists in a mesh.");


        return new DAGRegistration(context);

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
    public Context get(String id) {
        return registered.get(id);
    }

    @Override
    public Optional<Context> find(String id) {
        return Optional.ofNullable(registered.get(id));
    }

    @Override
    public Set<String> getVisibleContexts(String id) {
        return graphView.successors(id);
    }

    @Override
    public Set<String> getDependingContexts(String id) {
        return graphView.predecessors(id);
    }

    @Override
    public List<String> getTransitiveContexts(String fromId) {
        return reachableContextsCache.computeIfAbsent(fromId, graphView::bfs);
    }

    private void invalidateCache() {
        reachableContextsCache.clear();
    }

    @Override
    public <T> boolean canLookup(String fromId, TypedKey<T> key) {

        if (!contains(fromId))
            throw new IllegalArgumentException("Context \"" + fromId + "\" is not registered in the mesh.");

        return getTransitiveContexts(fromId).stream()
                .map(this::get)
                .anyMatch(context -> context.getResolver().contains(key));
    }

    @Override
    public <T> Optional<ContextMeshLookup<T>> lookup(String fromId, TypedKey<T> key) {

        if (!contains(fromId))
            throw new IllegalArgumentException("Context \"" + fromId + "\" is not registered in the mesh.");

        return getTransitiveContexts(fromId).stream()
                .map(this::get)
                .filter(context -> context.getBindingContainer().contains(key))
                .findFirst()
                .map(context -> new ContextMeshLookup<>(
                        context,
                        context.getBindingContainer().get(key)
                ));
    }

    @Override
    public void dispose() throws DisposeException {
        if (disposed.compareAndSet(false, true)) {
            registered.clear();
            registrationOrder.clear();
            reachableContextsCache.clear();
            //graph.clear()
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
