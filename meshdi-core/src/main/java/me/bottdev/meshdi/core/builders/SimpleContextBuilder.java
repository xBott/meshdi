package me.bottdev.meshdi.core.builders;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependencyResolver;
import me.bottdev.kern.dependency.exceptions.DependencyException;
import me.bottdev.kern.dependency.graph.GraphDependencyResolver;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.BindingBuildException;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.core.SimpleBeanLifecycleManager;
import me.bottdev.meshdi.core.SimpleBindingContainer;
import me.bottdev.meshdi.core.bindings.ConstructorBinding;
import me.bottdev.meshdi.core.bindings.FactoryBinding;
import me.bottdev.meshdi.core.context.SimpleContext;
import me.bottdev.meshdi.core.resolvers.SimpleBeanResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimpleContextBuilder implements ContextBuilder<SimpleContext> {

    //private DependencyResolver dependencyResolver = new GraphDependencyResolver();

    private String id;
    private Map<TypedKey<?>, BindingBuilder<?>> bindingBuilders;
    private BeanLifecycleManager lifecycleManager;

    public SimpleContextBuilder() {
        this.bindingBuilders = new HashMap<>();

//        Registry<ScopeType, BeanScope> scopeRegistry = new SimpleRegistry<>();
//        scopeRegistry.register(ScopeType.SINGLETON, new SingletonScope());
//        scopeRegistry.register(ScopeType.PROTOTYPE, new PrototypeScope());
        this.lifecycleManager = new SimpleBeanLifecycleManager();
    }

    public SimpleContextBuilder id(String id) {
        this.id = id;
        return this;
    }

    public SimpleContextBuilder lifecycleManager(BeanLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
        return this;
    }

    public <T> SimpleContextBuilder binding(BindingBuilder<T> bindingBuilder) {
        bindingBuilders.put(bindingBuilder.getKey(), bindingBuilder);
        return this;
    }

    public <T> SimpleContextBuilder factory(TypedKey<T> key, Consumer<FactoryBinding.Builder<T>> config) {
        FactoryBinding.Builder<T> bindingBuilder = new FactoryBinding.Builder<>(key);
        config.accept(bindingBuilder);
        return binding(bindingBuilder);
    }

    public <T> SimpleContextBuilder construct(TypedKey<T> key, Consumer<ConstructorBinding.Builder<T>> config) {
        ConstructorBinding.Builder<T> bindingBuilder = new ConstructorBinding.Builder<>(key);
        config.accept(bindingBuilder);
        return binding(bindingBuilder);
    }

    public SimpleContext build(
            Function<BindingContainer, BeanResolver> resolverFactory
    ) throws ContextBuildException {
        try {

            Objects.requireNonNull(id, "Context Id must be non-null.");
            Objects.requireNonNull(lifecycleManager, "Context Lifecycle Manager must be non-null.");
            if (id.isBlank()) throw new IllegalArgumentException("Context Id must be non-blank.");

            Map<TypedKey<?>, Binding<?>> bindings = new HashMap<>();

            for (BindingBuilder<?> bindingBuilder : bindingBuilders.values()) {
                TypedKey<?> key = bindingBuilder.getKey();
                Binding<?> binding = bindingBuilder.build();
                bindings.put(key, binding);
            }

            BindingContainer bindingContainer = new SimpleBindingContainer(bindings);
            //dependencyResolver.resolve(bindingContainer);

            BeanResolver resolver = resolverFactory.apply(bindingContainer);
            return new SimpleContext(id, bindingContainer, lifecycleManager, resolver);

        } catch (BindingBuildException ex) {
            throw new ContextBuildException("Failed to create binding.", ex);

//        } catch (DependencyException ex) {
//            throw new ContextBuildException("Failed to resolve context dependencies.", ex);

        } catch (Exception ex) {
            throw new ContextBuildException("Failed to build a context.", ex);

        }
    }

    @Override
    public SimpleContext build() throws ContextBuildException {
        return build(bindingContainer -> new SimpleBeanResolver(bindingContainer, lifecycleManager));
    }

}
