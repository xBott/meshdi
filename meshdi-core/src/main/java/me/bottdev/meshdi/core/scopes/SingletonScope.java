package me.bottdev.meshdi.core.scopes;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.BeanInstance;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.BeanScope;
import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/// Scope that keeps exactly one instance per binding key.
///
/// Creation is atomic per key: concurrent callers wait for the single creation and receive the same instance.
/// An instance becomes visible only after its `POST_CONSTRUCT` handler has completed, so a failed handler
/// leaves nothing cached and the next request retries the creation.
public class SingletonScope implements BeanScope {

    /// Per-key creation state. The lock is private to the scope, so user code can never contend on it.
    private static final class Holder {
        private final ReentrantLock lock = new ReentrantLock();
        private volatile BeanInstance<?> instance;
        private boolean creating;
    }

    private final Map<TypedKey<?>, Holder> holders = new ConcurrentHashMap<>();
    private final Deque<BeanInstance<?>> creationOrder = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /// @return instances in reverse creation order, so dependents are destroyed before their dependencies.
    @Override
    public List<BeanInstance<?>> getDestroyOrder() {
        return new ArrayList<>(creationOrder);
    }

    @Override
    public <T> boolean contains(TypedKey<T> key) {
        return get(key) != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> BeanInstance<T> get(TypedKey<T> key) {
        Holder holder = holders.get(key);
        return holder == null ? null : (BeanInstance<T>) holder.instance;
    }

    @Override
    public <T> T create(Binding<T> binding, BeanResolver resolver)
            throws BeanCreationException, BeanLifecycleEventHandleException {
        return createOrGet(binding, resolver, true);
    }

    @Override
    public <T> T getOrCreate(Binding<T> binding, BeanResolver resolver)
            throws BeanCreationException, BeanLifecycleEventHandleException {
        BeanInstance<T> existing = get(binding.getKey());
        if (existing != null) return existing.value();
        return createOrGet(binding, resolver, false);
    }

    @SuppressWarnings("unchecked")
    private <T> T createOrGet(Binding<T> binding, BeanResolver resolver, boolean failIfExists)
            throws BeanCreationException, BeanLifecycleEventHandleException {

        TypedKey<T> key = binding.getKey();
        if (disposed.get())
            throw new BeanCreationException("Singleton scope is disposed, cannot create " + key + ".");

        Holder holder = holders.computeIfAbsent(key, _ -> new Holder());
        holder.lock.lock();
        try {
            BeanInstance<T> existing = (BeanInstance<T>) holder.instance;
            if (existing != null) {
                if (failIfExists) throw new BeanCreationException("Singleton " + key + " is already created");
                return existing.value();
            }
            // The lock is reentrant, so a bean that requests itself while being created ends up here again.
            if (holder.creating)
                throw new BeanCreationException("Singleton " + key + " depends on itself while being created.");

            holder.creating = true;
            try {
                T value = binding.create(resolver);
                binding.invokeEventHandler(BeanLifecycleEventType.POST_CONSTRUCT, value);

                BeanInstance<T> instance = new BeanInstance<>(binding, value);
                if (disposed.get())
                    throw new BeanCreationException("Singleton scope was disposed while creating " + key + ".");
                holder.instance = instance;
                creationOrder.push(instance);
                return value;
            } finally {
                holder.creating = false;
            }
        } finally {
            holder.lock.unlock();
        }
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            holders.clear();
            creationOrder.clear();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
