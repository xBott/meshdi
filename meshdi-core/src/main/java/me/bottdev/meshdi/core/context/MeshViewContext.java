package me.bottdev.meshdi.core.context;

import lombok.Getter;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.core.resolvers.MeshBeanResolver;

import java.util.Objects;

public class MeshViewContext extends AbstractContext {

    private final Context delegate;
    @Getter
    private final BeanResolver resolver;

    public MeshViewContext(
            Context delegate,
            MeshBeanResolver resolver
    ) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate Context must be non-null.");
        this.resolver = Objects.requireNonNull(resolver, "Mesh Bean Resolver must be non-null.");
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public BindingContainer getBindingContainer() {
        return delegate.getBindingContainer();
    }

    @Override
    public BeanLifecycleManager getLifecycleManager() {
        return delegate.getLifecycleManager();
    }

    @Override
    protected void onDispose() {
        delegate.dispose();
    }

}
