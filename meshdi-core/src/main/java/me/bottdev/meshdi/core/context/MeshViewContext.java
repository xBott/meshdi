package me.bottdev.meshdi.core.context;

import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.core.resolvers.MeshBeanResolver;

import java.util.Objects;

public class MeshViewContext extends AbstractContext {

    private final Context delegate;
    private final BeanResolver resolver;

    public MeshViewContext(
            Context delegate,
            MeshBeanResolver resolver
    ) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate Context must be non-null.");
        this.resolver = Objects.requireNonNull(resolver, "Mesh Bean Resolver must be non-null.");
    }

    @Override
    public ContextState state() {
        return state;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public BindingContainer bindingManager() {
        return delegate.bindingManager();
    }

    @Override
    public BeanLifecycleManager lifecycleManager() {
        return delegate.lifecycleManager();
    }

    @Override
    public BeanResolver resolver() {
        return resolver;
    }

    @Override
    protected void onDispose() {
        delegate.dispose();
    }

}
