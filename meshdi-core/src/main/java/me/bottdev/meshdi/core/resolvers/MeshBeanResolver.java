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
    private final ContextMesh mesh;

    @Override
    public <T> boolean contains(TypedKey<T> key) {
        if (bindingContainer.containsBinding(key)) return true;
        return mesh.canLookup(contextId, key);
    }

    @Override
    public <T> T get(TypedKey<T> key) {

        if (bindingContainer.containsBinding(key)) {

            Binding<T> binding = bindingContainer.getBinding(key);

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
                BeanLifecycleManager targetLifecycleManager = lookup.owner().lifecycleManager();
                BeanResolver targetResolver = lookup.owner().resolver();
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

        if (bindingContainer.containsBinding(key)) {
            Binding<T> binding = bindingContainer.getBinding(key);

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
                BeanLifecycleManager targetLifecycleManager = lookup.owner().lifecycleManager();
                BeanResolver targetResolver = lookup.owner().resolver();
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
        if (bindingContainer.containsBinding(key)) return bindingContainer.getBinding(key);
        return mesh.lookup(contextId, key).map(ContextMeshLookup::binding).orElse(null);
    }

}
