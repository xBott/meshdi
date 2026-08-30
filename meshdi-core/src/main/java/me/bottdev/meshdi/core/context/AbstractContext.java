package me.bottdev.meshdi.core.context;

import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.ContextState;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.exceptions.*;
import me.bottdev.meshdi.core.reflection.BeanInstantiator;
import me.bottdev.meshdi.core.reflection.SimpleBeanInstantiator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractContext implements Context {

    protected ContextState state = ContextState.CREATED;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private final Map<Class<?>, BeanInstantiator<?>> autowireCache = new ConcurrentHashMap<>();

    protected void requireState(ContextState state) throws IllegalStateException {
        if (this.state != state)
            throw new IllegalStateException("Expected context state \"" + state + "\", but got \"" + this.state + "\".");
    }

    @Override
    public void start() throws ContextStartException {

        try {
            requireState(ContextState.CREATED);

            initializeEagerBindings();
            state = ContextState.STARTED;

        } catch (BeanLifecycleException ex) {
            throw new ContextStartException("Failed to initialize eager entries.", ex);

        } catch (Exception ex) {
            throw new ContextStartException("Could not start context.", ex);

        }

    }

    protected void initializeEagerBindings() throws BeanLifecycleException {
        for (Binding<?> binding : bindingManager().getBindings()) {
            if (binding.getInitializationStrategy() != InitializationStrategy.EAGER) continue;
            lifecycleManager().getOrCreate(binding, resolver());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T autowire(Class<? extends T> implementation) {
        BeanInstantiator<T> instantiator = (BeanInstantiator<T>) autowireCache.computeIfAbsent(
                implementation,
                _ -> {
                    try {
                        return new SimpleBeanInstantiator<>(implementation);

                    } catch (BeanConstructorException ex) {
                        throw new BeanAutowireException("Cannot autowire class " + implementation.getName(), ex);

                    }
                }
        );

        try {
            return instantiator.instantiate(resolver());

        } catch (BeanCreationException ex) {
            throw new BeanAutowireException("Failed to instantiate " + implementation.getName(), ex);

        }
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            onDispose();
            state = ContextState.DISPOSED;
        }
    }

    protected abstract void onDispose();

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }


}
