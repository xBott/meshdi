package me.bottdev.meshdi.core.context;

import lombok.Getter;
import me.bottdev.meshdi.api.BeanLifecycleManager;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.BindingContainer;
import me.bottdev.meshdi.api.ContextState;

import java.util.Objects;

@Getter
public class SimpleContext extends AbstractContext {

    private final String id;
    private final BindingContainer bindingContainer;
    private final BeanLifecycleManager lifecycleManager;
    private final BeanResolver resolver;

    public SimpleContext(
            String id,
            BindingContainer bindingContainer,
            BeanLifecycleManager lifecycleManager,
            BeanResolver resolver
    ) {
        this.id = Objects.requireNonNull(id, "Context Id must be non-null.");
        this.bindingContainer = Objects.requireNonNull(bindingContainer, "Binding Container must be non-null.");
        this.lifecycleManager = Objects.requireNonNull(lifecycleManager, "Bean Lifecycle Manager must be non-null.");
        this.resolver = Objects.requireNonNull(resolver, "Bean Resolver must be non-null.");

    }

    @Override
    protected void onDispose() {
        lifecycleManager.dispose();
        bindingContainer.dispose();
    }

    @Override
    public ContextState state() {
        return state;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public BindingContainer bindingManager() {
        return bindingContainer;
    }

    @Override
    public BeanLifecycleManager lifecycleManager() {
        return lifecycleManager;
    }

    @Override
    public BeanResolver resolver() {
        return resolver;
    }

}
