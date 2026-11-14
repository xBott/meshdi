package me.bottdev.meshdi.core.scopes;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.BeanInstance;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.BeanScope;
import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/// Scope that creates a new instance on every request.
///
/// Prototype instances are **unmanaged** after creation: the scope does not keep a reference to them,
/// so `PRE_DESTROY` handlers and `Disposable.dispose()` are never invoked by the container.
/// Whoever requests a prototype owns it and must release its resources.
/// Tracking them here would pin every instance (and its class loader) for the lifetime of the context.
public class PrototypeScope implements BeanScope {

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    @Override
    public List<BeanInstance<?>> getDestroyOrder() {
        return List.of();
    }

    @Override
    public <T> boolean contains(TypedKey<T> key) {
        return false;
    }

    @Override
    public <T> BeanInstance<T> get(TypedKey<T> key) {
        return null;
    }

    @Override
    public <T> T create(Binding<T> binding, BeanResolver resolver)
            throws BeanCreationException, BeanLifecycleEventHandleException {
        if (disposed.get())
            throw new BeanCreationException("Prototype scope is disposed, cannot create " + binding.getKey() + ".");
        T value = binding.create(resolver);
        binding.invokeEventHandler(BeanLifecycleEventType.POST_CONSTRUCT, value);
        return value;
    }

    @Override
    public <T> T getOrCreate(Binding<T> binding, BeanResolver resolver)
            throws BeanCreationException, BeanLifecycleEventHandleException {
        return create(binding, resolver);
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
