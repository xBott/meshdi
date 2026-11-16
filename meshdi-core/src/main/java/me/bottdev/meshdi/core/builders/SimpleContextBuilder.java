package me.bottdev.meshdi.core.builders;

import lombok.NonNull;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.commons.registry.Registry;
import me.bottdev.kern.commons.registry.types.SimpleRegistry;
import me.bottdev.kern.dependency.DependencyResolver;
import me.bottdev.kern.dependency.simple.graph.GraphDependencyResolver;
import me.bottdev.kern.struct.algorithms.cycle.SimpleCycleDetector;
import me.bottdev.kern.struct.algorithms.sort.KahnSorter;
import me.bottdev.meshdi.api.*;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.meshdi.api.diagnostic.BeanDestroyFailed;
import me.bottdev.meshdi.api.exceptions.BindingBuildException;
import me.bottdev.meshdi.api.exceptions.DuplicateBindingException;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.core.SimpleBeanLifecycleManager;
import me.bottdev.meshdi.core.SimpleBindingContainer;
import me.bottdev.meshdi.api.bindings.ConstructorBindingBuilder;
import me.bottdev.meshdi.api.bindings.FactoryBindingBuilder;
import me.bottdev.meshdi.core.bindings.ConstructorBinding;
import me.bottdev.meshdi.core.bindings.FactoryBinding;
import me.bottdev.meshdi.core.context.SimpleContext;
import me.bottdev.meshdi.core.resolvers.SimpleBeanResolver;
import me.bottdev.meshdi.core.scopes.PrototypeScope;
import me.bottdev.meshdi.core.scopes.SingletonScope;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimpleContextBuilder implements ContextBuilder {

    private static final DependencyResolver dependencyResolver = new GraphDependencyResolver(new KahnSorter(new SimpleCycleDetector()));

    private String id;
    private final Map<TypedKey<?>, BindingBuilder<?>> bindingBuilders;
    private BeanLifecycleManager lifecycleManager;

    public SimpleContextBuilder() {
        this.bindingBuilders = new LinkedHashMap<>();
        this.lifecycleManager = new SimpleBeanLifecycleManager(defaultScopes());
    }

    @Override
    public SimpleContextBuilder id(@NonNull String id) {
        this.id = id;
        return this;
    }

    @Override
    public SimpleContextBuilder lifecycleManager(@NonNull BeanLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
        return this;
    }

    @Override
    public <T> SimpleContextBuilder binding(@NonNull BindingBuilder<T> bindingBuilder) {
        TypedKey<T> key = bindingBuilder.getKey();
        if (bindingBuilders.putIfAbsent(key, bindingBuilder) != null) {
            throw new DuplicateBindingException(key);
        }
        return this;
    }

    @Override
    public <T> SimpleContextBuilder override(@NonNull BindingBuilder<T> bindingBuilder) {
        bindingBuilders.put(bindingBuilder.getKey(), bindingBuilder);
        return this;
    }

    /// Sets the sink that receives beans that failed to be destroyed when the context is disposed.
    /// Replaces the lifecycle manager with a default one that reports to the sink.
    public SimpleContextBuilder destroyFailureSink(@NonNull DiagnosticSink<BeanDestroyFailed> sink) {
        this.lifecycleManager = new SimpleBeanLifecycleManager(defaultScopes(), sink);
        return this;
    }

    private static Registry<ScopeType, BeanScope> defaultScopes() {
        Registry<ScopeType, BeanScope> scopeRegistry = new SimpleRegistry<>();
        scopeRegistry.register(ScopeType.SINGLETON, new SingletonScope());
        scopeRegistry.register(ScopeType.PROTOTYPE, new PrototypeScope());
        return scopeRegistry;
    }

    @Override
    public <T> SimpleContextBuilder factory(
            @NonNull TypedKey<T> key,
            @NonNull Consumer<FactoryBindingBuilder<T>> config
    ) {
        FactoryBinding.Builder<T> bindingBuilder = new FactoryBinding.Builder<>(key);
        config.accept(bindingBuilder);
        return binding(bindingBuilder);
    }

    @Override
    public <T> SimpleContextBuilder construct(
            @NonNull TypedKey<T> key,
            @NonNull Consumer<ConstructorBindingBuilder<T>> config
    ) {
        ConstructorBinding.Builder<T> bindingBuilder = new ConstructorBinding.Builder<>(key);
        config.accept(bindingBuilder);
        return binding(bindingBuilder);
    }

    @Override
    public Map<TypedKey<?>, BindingBuilder<?>> bindings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(bindingBuilders));
    }

    @Override
    public ContextBuilder merge(@NonNull ContextBuilder other) {

        Map<TypedKey<?>, BindingBuilder<?>> otherBindings = other.bindings();
        for (TypedKey<?> key : otherBindings.keySet()) {
            if (bindingBuilders.containsKey(key)) throw new DuplicateBindingException(key);

        }

        bindingBuilders.putAll(otherBindings);
        return this;
    }

    public SimpleContext build(
            Function<BindingContainer, BeanResolver> resolverFactory
    ) throws ContextBuildException {
        try {

            Objects.requireNonNull(id, "Context Id must be non-null.");
            Objects.requireNonNull(lifecycleManager, "Context Lifecycle Manager must be non-null.");
            if (id.isBlank()) throw new IllegalArgumentException("Context Id must be non-blank.");

            Map<TypedKey<?>, Binding<?>> bindings = new LinkedHashMap<>();

            for (BindingBuilder<?> bindingBuilder : bindingBuilders.values()) {
                TypedKey<?> key = bindingBuilder.getKey();
                Binding<?> binding = bindingBuilder.build();
                bindings.put(key, binding);
            }

            // Validates the graph (missing, duplicate, circular) and yields the topological order.
            List<Binding<?>> ordered = dependencyResolver
                    .resolve(new SimpleBindingContainer(bindings))
                    .unwrapOrThrow()
                    .ordered();

            // Bindings are stored in that order, so eager beans are initialised dependencies-first.
            Map<TypedKey<?>, Binding<?>> orderedBindings = new LinkedHashMap<>();
            for (Binding<?> binding : ordered) {
                orderedBindings.put(binding.getKey(), binding);
            }
            // Keep anything the resolver did not return, in registration order.
            bindings.forEach(orderedBindings::putIfAbsent);

            BindingContainer bindingContainer = new SimpleBindingContainer(orderedBindings);
            BeanResolver resolver = resolverFactory.apply(bindingContainer);
            return new SimpleContext(id, bindingContainer, lifecycleManager, resolver);

        } catch (BindingBuildException ex) {
            throw new ContextBuildException("Failed to create binding.", ex);

        } catch (Exception ex) {
            throw new ContextBuildException("Failed to build a context.", ex);

        }
    }

    @Override
    public SimpleContext build() throws ContextBuildException {
        return build(bindingContainer -> new SimpleBeanResolver(bindingContainer, lifecycleManager));
    }

}
