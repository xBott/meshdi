package me.bottdev.meshdi.core;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.BindingContainer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleBindingContainer implements BindingContainer {

    /// Insertion-ordered and never mutated: disposing swaps the reference, so concurrent readers
    /// (mesh lookups from other threads) never observe a map that is being cleared.
    private volatile Map<TypedKey<?>, Binding<?>> bindings;

    /// @param bindings the bindings of the context. Iteration order is kept and defines eager initialisation order.
    public SimpleBindingContainer(Map<TypedKey<?>, Binding<?>> bindings) {
        this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }
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
            bindings = Map.of();
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
        return bindings.keySet();
    }

    @Override
    public Collection<Binding<?>> values() {
        return bindings.values();
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
        return bindings;
    }
}
