package me.bottdev.meshdi.core.mesh;

import me.bottdev.kern.commons.exceptions.DisposeException;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.mesh.MeshAmbiguousLookupException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;
import me.bottdev.meshdi.core.context.MeshViewContext;
import me.bottdev.meshdi.core.mesh.views.KernContextGraphView;
import me.bottdev.meshdi.core.resolvers.MeshBeanResolver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/// A [ContextMesh] whose contexts form a directed acyclic graph of visibility edges.
///
/// ## Thread safety
/// Readers (`lookup`, `canLookup`, `contains`, the graph queries) never lock: they work on an immutable
/// [Snapshot] that is replaced atomically whenever the mesh changes. Writers (`register`, unregister,
/// `dispose`) are serialised by a lock, mutate the graph and publish a new snapshot.
/// A lookup that races with a change sees either the mesh before it or the mesh after it, never a mix.
public class DagContextMesh implements ContextMesh {

    /// Owners of a key among the contexts reachable from one context. More than one owner is an ambiguity.
    private record KeyOwners(List<String> ids) {}

    /// Immutable view of the whole mesh.
    ///
    /// `keyIndex` is filled lazily per requesting context: it maps each key bound by a reachable context
    /// to its owners, which turns a lookup into one hash access instead of a walk over reachable contexts.
    private record Snapshot(
            Map<String, MeshRegistration> registered,
            Map<String, Set<String>> successors,
            Map<String, Set<String>> predecessors,
            Map<String, List<String>> reachable,
            Map<String, Map<TypedKey<?>, KeyOwners>> keyIndex
    ) {

        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), new ConcurrentHashMap<>());

        Map<TypedKey<?>, KeyOwners> keyOwnersOf(String fromId) {
            return keyIndex.computeIfAbsent(fromId, this::indexKeys);
        }

        private Map<TypedKey<?>, KeyOwners> indexKeys(String fromId) {
            Map<TypedKey<?>, List<String>> owners = new HashMap<>();
            for (String contextId : reachable.get(fromId)) {
                MeshRegistration registration = registered.get(contextId);
                if (registration == null) continue;
                for (TypedKey<?> key : registration.context().bindingManager().keys()) {
                    owners.computeIfAbsent(key, _ -> new ArrayList<>(1)).add(contextId);
                }
            }
            Map<TypedKey<?>, KeyOwners> index = new HashMap<>(owners.size() * 2);
            owners.forEach((key, ids) -> index.put(key, new KeyOwners(List.copyOf(ids))));
            return index;
        }

    }

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private final ReentrantLock writeLock = new ReentrantLock();
    /// Guarded by `writeLock`.
    private final LinkedHashMap<String, MeshRegistration> registered = new LinkedHashMap<>();
    /// Guarded by `writeLock`.
    private final ContextGraphView graphView = new KernContextGraphView();

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    /// Must be called with `writeLock` held.
    private void publishSnapshot() {
        Map<String, Set<String>> successors = new HashMap<>();
        Map<String, Set<String>> predecessors = new HashMap<>();
        Map<String, List<String>> reachable = new HashMap<>();
        for (String id : registered.keySet()) {
            successors.put(id, Set.copyOf(graphView.successors(id)));
            predecessors.put(id, Set.copyOf(graphView.predecessors(id)));
            reachable.put(id, List.copyOf(graphView.bfs(id)));
        }
        this.snapshot = new Snapshot(
                Collections.unmodifiableMap(new LinkedHashMap<>(registered)),
                successors,
                predecessors,
                reachable,
                new ConcurrentHashMap<>()
        );
    }

    private static void requireRegistered(Snapshot snapshot, String id) {
        if (!snapshot.registered().containsKey(id))
            throw new IllegalArgumentException("Context \"" + id + "\" does not exist in a mesh.");
    }

    @Override
    public boolean contains(String id) {
        return snapshot.registered().containsKey(id);
    }

    @Override
    public Context register(MeshRegistration registration)
            throws MeshRegisterException
    {

        Context context = registration.context();
        String contextId = context.id();
        List<String> sees = registration.sees();

        writeLock.lock();
        try {

            if (disposed.get())
                throw new MeshRegisterException("Mesh is disposed, cannot register context \"" + contextId + "\".");

            if (registered.containsKey(contextId))
                throw new MeshRegisterException("Context \"" + contextId + "\" already exists in a mesh.");

            // A new node has no incoming edges, so its outgoing edges can only close a cycle through itself.
            for (String targetId : sees) {
                if (targetId.equals(contextId))
                    throw new MeshRegisterException("Edge " + contextId + " -> " + targetId + " would create a cycle.");
                if (!registered.containsKey(targetId))
                    throw new MeshRegisterException("Context \"" + targetId + "\" does not exist in a mesh.");
            }

            try {
                Context viewContext = createViewContext(context);
                MeshRegistration viewRegistration = new MeshRegistration(viewContext, sees);

                registered.put(contextId, viewRegistration);
                graphView.addNode(contextId);
                for (String targetId : sees) {
                    graphView.addEdge(contextId, targetId);
                }

                publishSnapshot();
                return viewContext;

            } catch (Exception ex) {
                // Never leave a half-registered context behind.
                registered.remove(contextId);
                try {
                    graphView.removeNode(contextId);
                } catch (Exception removeFailure) {
                    ex.addSuppressed(removeFailure);
                }
                publishSnapshot();
                throw new MeshRegisterException("Failed to submit registration of context.", ex);

            }

        } finally {
            writeLock.unlock();
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

    private void unregister(String contextId) {
        writeLock.lock();
        try {
            if (registered.remove(contextId) == null) return;
            graphView.removeNode(contextId);
            publishSnapshot();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public MeshUnregisterCommand planUnregister(String id, MeshContextSelectionStrategy strategy)
            throws MeshContextSelectionException
    {

        if (!contains(id))
            throw new MeshContextSelectionException("Context \"" + id + "\" does not exist in a mesh.");

        List<String> contextIds = strategy.select(id, this);

        return new MeshUnregisterCommand(contextIds, this::unregister);
    }

    @Override
    public MeshRegistration get(String id) {
        return snapshot.registered().get(id);
    }

    @Override
    public List<Context> getContexts() {
        return snapshot.registered().values().stream()
                .map(MeshRegistration::context)
                .toList();
    }

    @Override
    public Optional<MeshRegistration> find(String id) {
        return Optional.ofNullable(get(id));
    }

    @Override
    public Set<String> getVisibleContexts(String id) {
        Snapshot current = snapshot;
        requireRegistered(current, id);
        return current.successors().get(id);
    }

    @Override
    public Set<String> getDependingContexts(String id) {
        Snapshot current = snapshot;
        requireRegistered(current, id);
        return current.predecessors().get(id);
    }

    @Override
    public List<String> getTransitiveContexts(String fromId) {
        Snapshot current = snapshot;
        requireRegistered(current, fromId);
        return current.reachable().get(fromId);
    }

    @Override
    public <T> boolean canLookup(String fromId, TypedKey<T> key) {
        Snapshot current = snapshot;
        if (!current.registered().containsKey(fromId))
            throw new IllegalArgumentException("Context \"" + fromId + "\" is not registered in the mesh.");

        return current.keyOwnersOf(fromId).containsKey(key);
    }

    @Override
    public <T> Optional<ContextMeshLookup<T>> lookup(String fromId, TypedKey<T> key) {
        Snapshot current = snapshot;
        if (!current.registered().containsKey(fromId))
            throw new IllegalArgumentException("Context \"" + fromId + "\" is not registered in the mesh.");

        KeyOwners owners = current.keyOwnersOf(fromId).get(key);
        if (owners == null) return Optional.empty();
        if (owners.ids().size() > 1)
            throw new MeshAmbiguousLookupException(fromId, key, owners.ids());

        Context context = current.registered().get(owners.ids().getFirst()).context();
        // The owner may have been disposed after this snapshot was taken.
        Binding<T> binding = context.bindingManager().getBinding(key);
        if (binding == null) return Optional.empty();

        return Optional.of(new ContextMeshLookup<>(context, binding));
    }

    @Override
    public void dispose() throws DisposeException {
        if (disposed.compareAndSet(false, true)) {
            writeLock.lock();
            try {
                registered.reversed().forEach((_, registration) ->
                        registration.context().dispose()
                );
                registered.clear();
                graphView.clear();
                snapshot = Snapshot.EMPTY;
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
