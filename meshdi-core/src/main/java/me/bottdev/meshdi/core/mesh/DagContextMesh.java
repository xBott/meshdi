package me.bottdev.meshdi.core.mesh;

import me.bottdev.kern.commons.exceptions.DisposeException;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;
import me.bottdev.meshdi.core.context.MeshViewContext;
import me.bottdev.meshdi.core.mesh.views.KernContextGraphView;
import me.bottdev.meshdi.core.resolvers.MeshBeanResolver;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DagContextMesh implements ContextMesh {

    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final LinkedHashMap<String, MeshRegistration> registered = new LinkedHashMap<>();
    private final ContextGraphView graphView = new KernContextGraphView();
    private final Map<String, List<String>> reachableContextsCache = new HashMap<>();

    @Override
    public boolean contains(String id) {
        return registered.containsKey(id);
    }

    @Override
    public Context register(MeshRegistration registration)
            throws MeshRegisterException
    {

        Context context = registration.context();
        String contextId = context.id();
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

            Context viewContext = createViewContext(context);
            MeshRegistration viewRegistration = new MeshRegistration(viewContext, sees);
            registered.put(contextId, viewRegistration);

            graphView.addNode(contextId);
            for (String targetId : sees) {
                graphView.addEdge(contextId, targetId);
            }

            invalidateCache();

            return viewContext;

        } catch (Exception ex) {
            throw new MeshRegisterException("Failed to submit registration of context.", ex);

        }

    }

    private Context createViewContext(Context delegate) {
        MeshBeanResolver resolver = new MeshBeanResolver(
                delegate.id(),
                delegate.bindingManager(),
                delegate.lifecycleManager(),
                this
        );
        return new MeshViewContext(delegate, resolver);
    }

    @Override
    public MeshUnregisterCommand planUnregister(String id, MeshContextSelectionStrategy strategy)
            throws MeshContextSelectionException
    {
        if (!contains(id))
            throw new MeshContextSelectionException("Context \"" + id + "\" does not exist in a mesh.");

        List<String> contextIds = strategy.select(id, this);

        return new MeshUnregisterCommand(contextIds, contextId -> {
            graphView.removeNode(contextId);
            registered.remove(contextId);
            invalidateCache();
        });
    }

    @Override
    public MeshRegistration get(String id) {
        return registered.get(id);
    }

    @Override
    public List<Context> getContexts() {
        return registered.values().stream()
                .map(MeshRegistration::context)
                .toList();
    }

    @Override
    public Optional<MeshRegistration> find(String id) {
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
            MeshRegistration reg = registered.get(string);
            if (reg != null && reg.context().resolver().contains(key)) {
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
            MeshRegistration registration = registered.get(string);
            if (registration == null) continue;

            Context context = registration.context();

            if (!context.bindingManager().containsBinding(key)) continue;

            ContextMeshLookup<T> lookup = new ContextMeshLookup<>(
                    context,
                    context.bindingManager().getBinding(key)
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
