package me.bottdev.meshdi.core.scopes;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.BeanScope;
import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrototypeScope implements BeanScope {

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    @Override
    public List<Object> getDestroyOrder() {
        return List.of();
    }

    @Override
    public <T> boolean contains(TypedKey<T> key) {
        return false;
    }

    @Override
    public <T> T get(TypedKey<T> key) {
        return null;
    }

    @Override
    public <T> T create(Binding<T> binding, BeanResolver resolver) throws BeanCreationException {
        return binding.create(resolver);
    }

    @Override
    public void dispose() {
        disposed.compareAndSet(false, true);
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
