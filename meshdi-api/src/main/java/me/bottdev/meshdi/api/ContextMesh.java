package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.ContextMeshRegistrationException;

import java.util.List;
import java.util.Optional;

public interface ContextMesh extends Disposable {

    interface Registration {

        ContextMesh submit() throws ContextMeshRegistrationException;

    }

    boolean contains(String id);

    Registration register(Context context) throws ContextMeshRegistrationException;

    Context get(String id);

    Optional<Context> find(String id);

    List<Context> getReachableContexts(String fromId);

    <T> boolean canReach(String fromId, TypedKey<T> key);

    <T> Optional<ContextMeshLookup<T>> lookup(String from, TypedKey<T> key);



}
