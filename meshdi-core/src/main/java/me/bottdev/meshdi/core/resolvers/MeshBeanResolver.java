package me.bottdev.meshdi.core.resolvers;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;

import java.util.Optional;

@RequiredArgsConstructor
public class MeshBeanResolver implements BeanResolver {

    private final String contextId;
    private final BindingContainer bindingContainer;
    private final BeanLifecycleManager lifecycleManager;
    private final ContextMesh<?> mesh;

    @Override
    public <T> boolean contains(TypedKey<T> key) {
        if (bindingContainer.contains(key)) return true;
        return mesh.canLookup(contextId, key);
    }

    @Override
    public <T> T get(TypedKey<T> key) {
        if (bindingContainer.contains(key)) {
            Binding<T> binding = bindingContainer.get(key);

            try {
                return lifecycleManager.getOrCreate(binding, this);

            } catch (BeanLifecycleException ex) {
                throw new BeanResolvationException(key, "Could not create bean " + key + ".", ex);

            }
        }

        Optional<ContextMeshLookup<T>> lookupOptional = mesh.lookup(contextId, key);
        if (lookupOptional.isPresent()) {
            ContextMeshLookup<T> lookup = lookupOptional.get();

            try {
                BeanLifecycleManager targetLifecycleManager = lookup.owner().getLifecycleManager();
                BeanResolver targetResolver = lookup.owner().getResolver();
                Binding<T> targetBinding = lookup.binding();

                return targetLifecycleManager.getOrCreate(targetBinding, targetResolver);

            } catch (BeanLifecycleException ex) {
                throw new BeanResolvationException(key, "Could not create bean " + key + " found in mesh.", ex);

            }
        }

        return null;
    }

    @Override
    public <T> Optional<T> find(TypedKey<T> key) {
        if (bindingContainer.contains(key)) {
            Binding<T> binding = bindingContainer.get(key);

            try {
                T value = lifecycleManager.getOrCreate(binding, this);
                return Optional.of(value);

            } catch (BeanLifecycleException ex) {
                throw new BeanResolvationException(key, "Could not create bean " + key + ".", ex);

            }
        }

        Optional<ContextMeshLookup<T>> lookupOptional = mesh.lookup(contextId, key);
        if (lookupOptional.isPresent()) {
            ContextMeshLookup<T> lookup = lookupOptional.get();

            try {
                BeanLifecycleManager targetLifecycleManager = lookup.owner().getLifecycleManager();
                BeanResolver targetResolver = lookup.owner().getResolver();
                Binding<T> targetBinding = lookup.binding();

                T value = targetLifecycleManager.getOrCreate(targetBinding, targetResolver);
                return Optional.of(value);

            } catch (BeanLifecycleException ex) {
                throw new BeanResolvationException(key, "Could not create bean " + key + " found in mesh.", ex);

            }
        }

        return Optional.empty();
    }

    @Override
    public <T> Binding<T> getBinding(TypedKey<T> key) {
        if (bindingContainer.contains(key)) return bindingContainer.get(key);
        return mesh.lookup(contextId, key).map(ContextMeshLookup::binding).orElse(null);
    }

}
