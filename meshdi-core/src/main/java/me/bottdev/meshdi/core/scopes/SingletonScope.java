package me.bottdev.meshdi.core.scopes;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.BeanInstance;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.BeanScope;
import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class SingletonScope implements BeanScope {

    private final Map<TypedKey<?>, BeanInstance<?>> singletons = new ConcurrentHashMap<>();
    private final Deque<TypedKey<?>> creationOrder = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    @Override
    public List<BeanInstance<?>> getDestroyOrder() {
        List<BeanInstance<?>> destroyList = new ArrayList<>();

        Iterator<TypedKey<?>> it = creationOrder.iterator();
        while (it.hasNext()) {
            destroyList.add(singletons.get(it.next()));
        }

        return destroyList;
    }

    @Override
    public <T> boolean contains(TypedKey<T> key) {
        return singletons.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> BeanInstance<T> get(TypedKey<T> key) {
        return (BeanInstance<T>) singletons.get(key);
    }

    @Override
    public <T> T create(Binding<T> binding, BeanResolver resolver) throws BeanCreationException {

        TypedKey<T> key = binding.getKey();

        if (contains(key))
            throw new BeanCreationException("Singleton " + key + " is already created");

        synchronized (key) {

            if (contains(key))
                throw new BeanCreationException("Singleton " + key + " is already created");

            T value = binding.create(resolver);
            BeanInstance<T> instance = new BeanInstance<>(binding, value);

            singletons.put(key, instance);
            creationOrder.push(key);

            return value;

        }

    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            singletons.clear();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
