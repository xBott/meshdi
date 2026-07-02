package me.bottdev.meshdi.core;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.exceptions.DisposeException;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.api.ContextMeshLookup;
import me.bottdev.meshdi.api.exceptions.ContextMeshRegistrationException;

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
                throws ContextMeshRegistrationException
        {

            for (String contextId : sees) {
                if (contains(contextId)) continue;
                throw new ContextMeshRegistrationException("Context \"" + contextId + "\" does not exist in a mesh.");
            }

            try {

                registered.put(context.getId(), context);
                return DAGContextMesh.this;

            } catch (Exception ex) {
                throw new ContextMeshRegistrationException("Failed to submit registration of context.", ex);

            }

        }
    }

    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final Map<String, Context> registered = new HashMap<>();

    @Override
    public boolean contains(String id) {
        return registered.containsKey(id);
    }

    @Override
    public DAGRegistration register(Context context)
            throws ContextMeshRegistrationException
    {

        String contextId = context.getId();
        if (contains(contextId))
            throw new ContextMeshRegistrationException("Context \"" + contextId + "\" already exists in a mesh.");


        return new DAGRegistration(context);

    }

    @Override
    public Context get(String id) {
        return null;
    }

    @Override
    public Optional<Context> find(String id) {
        return Optional.empty();
    }

    @Override
    public List<Context> getReachableContexts(String fromId) {
        return List.of();
    }

    @Override
    public <T> boolean canReach(String fromId, TypedKey<T> key) {
        return false;
    }

    @Override
    public <T> Optional<ContextMeshLookup<T>> lookup(String from, TypedKey<T> key) {
        return Optional.empty();
    }

    @Override
    public void dispose() throws DisposeException {
        if (disposed.compareAndSet(false, true)) {

        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
