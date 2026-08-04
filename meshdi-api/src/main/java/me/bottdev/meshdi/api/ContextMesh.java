package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterPlanException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContextMesh<R extends MeshRegistration> extends Disposable {

    boolean contains(String id);

    void register(R registration) throws MeshRegisterException;

    MeshUnregisterCommand planUnregister(String id, MeshUnregisterStrategy strategy)
            throws MeshUnregisterPlanException;

    R get(String id);

    Optional<R> find(String id);

    Set<String> getVisibleContexts(String id);

    Set<String> getDependingContexts(String id);

    List<String> getTransitiveContexts(String fromId);

    <T> boolean canLookup(String fromId, TypedKey<T> key);

    <T> Optional<ContextMeshLookup<T>> lookup(String fromId, TypedKey<T> key);

}
