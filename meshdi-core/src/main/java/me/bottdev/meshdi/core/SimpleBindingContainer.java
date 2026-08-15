package me.bottdev.meshdi.core;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.BindingContainer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class SimpleBindingContainer implements BindingContainer {

    private final Map<TypedKey<?>, Binding<?>> bindings;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    @Override
    public <T> boolean containsBinding(TypedKey<T> key) {
        return bindings.containsKey(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Binding<T> getBinding(TypedKey<T> key) {
        Binding<?> binding = bindings.get(key);
        if (binding == null) return null;

        if (binding.getKey().type() != key.type()) {
            throw new ClassCastException("Binding type mismatch for key: " + key.qualifier());
        }

        return (Binding<T>) binding;
    }

    @Override
    public List<Binding<?>> getBindings() {
        return List.copyOf(bindings.values());
    }

    @Override
    public int size() {
        return bindings.size();
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            bindings.clear();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    @Override
    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    @Override
    public Set<TypedKey<?>> keys() {
        return Collections.unmodifiableSet(bindings.keySet());
    }

    @Override
    public Collection<Binding<?>> values() {
        return Collections.unmodifiableCollection(bindings.values());
    }

    @Override
    public boolean contains(TypedKey<?> typedKey) {
        return containsBinding(typedKey);
    }

    @Override
    public Binding<?> get(TypedKey<?> typedKey) {
        return getBinding(typedKey);
    }

    @Override
    public Map<TypedKey<?>, Binding<?>> toMap() {
        return Map.copyOf(bindings);
    }
}
