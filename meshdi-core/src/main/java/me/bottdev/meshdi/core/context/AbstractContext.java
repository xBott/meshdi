package me.bottdev.meshdi.core.context;

import lombok.Getter;
import me.bottdev.meshdi.api.Binding;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.ContextState;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractContext implements Context {

    @Getter
    protected ContextState state = ContextState.CREATED;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

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
        for (Binding<?> binding : getBindingContainer().getBindings()) {
            if (binding.getInitializationStrategy() != InitializationStrategy.EAGER) continue;
            getLifecycleManager().getOrCreate(binding, getResolver());
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
