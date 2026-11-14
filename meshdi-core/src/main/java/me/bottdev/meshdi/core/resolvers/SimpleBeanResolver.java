package me.bottdev.meshdi.core.resolvers;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.BeanLifecycleManager;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.BindingContainer;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;

import java.util.Optional;

@RequiredArgsConstructor
public class SimpleBeanResolver implements BeanResolver {

    private final BindingContainer bindingContainer;
    private final BeanLifecycleManager lifecycleManager;

    @Override
    public <T> boolean contains(TypedKey<T> key) {
        return bindingContainer.containsBinding(key);
    }

    @Override
    public <T> T get(TypedKey<T> key) {
        return find(key).orElseThrow(() -> new BeanResolvationException(
                key, "No binding found for " + key + "."
        ));
    }

    @Override
    public <T> Optional<T> find(TypedKey<T> key) {

        Binding<T> binding = bindingContainer.getBinding(key);
        if (binding == null) return Optional.empty();

        try {
            return Optional.of(lifecycleManager.getOrCreate(binding, this));

        } catch (BeanLifecycleException ex) {
            throw new BeanResolvationException(key, "Could not create bean " + key + ".", ex);

        }
    }

    @Override
    public <T> Binding<T> getBinding(TypedKey<T> key) {
        return bindingContainer.getBinding(key);
    }

}
