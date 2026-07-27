package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterPlanException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContextMesh extends Disposable {

    interface Registration {

        ContextMesh submit() throws MeshRegisterException;

    }

    boolean contains(String id);

    Registration register(Context context) throws MeshRegisterException;

    MeshUnregisterCommand planUnregister(String id, MeshUnregisterStrategy strategy)
            throws MeshUnregisterPlanException;

    Context get(String id);

    Optional<Context> find(String id);

    Set<String> getVisibleContexts(String id);

    Set<String> getDependingContexts(String id);

    List<String> getTransitiveContexts(String fromId);

    <T> boolean canLookup(String fromId, TypedKey<T> key);

    <T> Optional<ContextMeshLookup<T>> lookup(String fromId, TypedKey<T> key);

}
