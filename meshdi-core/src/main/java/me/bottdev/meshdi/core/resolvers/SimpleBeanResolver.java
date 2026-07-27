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
        return bindingContainer.contains(key);
    }

    @Override
    public <T> T get(TypedKey<T> key) {

        if (!bindingContainer.contains(key)) return null;
        Binding<T> binding = bindingContainer.get(key);

        try {
            return lifecycleManager.getOrCreate(binding, this);

        } catch (BeanLifecycleException ex) {
            throw new BeanResolvationException(key, "Could not create bean " + key + ".", ex);

        }

    }

    @Override
    public <T> Optional<T> find(TypedKey<T> key) {

        if (!bindingContainer.contains(key)) return null;
        Binding<T> binding = bindingContainer.get(key);

        try {
            return Optional.of(lifecycleManager.getOrCreate(binding, this));

        } catch (BeanLifecycleException ex) {
            throw new BeanResolvationException(key, "Could not create bean " + key + ".", ex);

        }
    }

    @Override
    public <T> Binding<T> getBinding(TypedKey<T> key) {
        return bindingContainer.get(key);
    }

}
