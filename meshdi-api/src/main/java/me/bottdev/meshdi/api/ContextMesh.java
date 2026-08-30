package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/// A special structure that connects several [Context] into one ecosystem.
public interface ContextMesh extends Disposable {

    boolean contains(String id);

    Context register(MeshRegistration registration) throws MeshRegisterException;

    MeshUnregisterCommand planUnregister(String id, MeshContextSelectionStrategy strategy)
            throws MeshContextSelectionException;

    MeshRegistration get(String id);

    /// @return Registered contexts in sorted order.
    List<Context> getContexts();

    Optional<MeshRegistration> find(String id);

    Set<String> getVisibleContexts(String id);

    Set<String> getDependingContexts(String id);

    /// @return All contexts that can be reached from specified one using BFS.
    List<String> getTransitiveContexts(String fromId);

    <T> boolean canLookup(String fromId, TypedKey<T> key);

    <T> Optional<ContextMeshLookup<T>> lookup(String fromId, TypedKey<T> key);

}
