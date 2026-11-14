package me.bottdev.meshdi.core.resolvers;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;

import java.util.Optional;

/// Resolver of a context registered in a [ContextMesh].
///
/// Local bindings win. Keys that are not bound locally are looked up in the contexts visible through the mesh,
/// and the bean is created by its owner context, so it follows the owner's lifecycle.
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
        return find(key).orElseThrow(() -> new BeanResolvationException(
                key, "No binding found for " + key + " in context \"" + contextId + "\" or the contexts it sees."
        ));
    }

    @Override
    public <T> Optional<T> find(TypedKey<T> key) {

        Binding<T> binding = bindingContainer.getBinding(key);
        if (binding != null) {
            try {
                return Optional.of(lifecycleManager.getOrCreate(binding, this));

            } catch (BeanLifecycleException ex) {
                throw new BeanResolvationException(key, "Could not create bean " + key + ".", ex);

            }
        }

        Optional<ContextMeshLookup<T>> lookupOptional = mesh.lookup(contextId, key);
        if (lookupOptional.isEmpty()) return Optional.empty();

        ContextMeshLookup<T> lookup = lookupOptional.get();
        try {
            BeanLifecycleManager targetLifecycleManager = lookup.owner().lifecycleManager();
            BeanResolver targetResolver = lookup.owner().resolver();
            return Optional.of(targetLifecycleManager.getOrCreate(lookup.binding(), targetResolver));

        } catch (BeanLifecycleException ex) {
            throw new BeanResolvationException(key, "Could not create bean " + key + " found in mesh.", ex);

        }
    }

    @Override
    public <T> Binding<T> getBinding(TypedKey<T> key) {
        Binding<T> binding = bindingContainer.getBinding(key);
        if (binding != null) return binding;
        return mesh.lookup(contextId, key).map(ContextMeshLookup::binding).orElse(null);
    }

}
