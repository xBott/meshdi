package me.bottdev.meshdi.core.bindings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.DependencyRequest;
import me.bottdev.kern.dependency.SimpleDependencyRequest;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;
import me.bottdev.meshdi.api.exceptions.BindingBuildException;

import java.util.*;

public class FactoryBinding<T> implements Binding<T> {

    @RequiredArgsConstructor
    public static class Builder<T> implements BindingBuilder<T> {

        @Getter
        private final TypedKey<T> key;
        private InitializationStrategy initializationStrategy = InitializationStrategy.LAZY;
        private ScopeType scopeType = ScopeType.SINGLETON;
        private BeanFactory<T> factory = null;
        private final List<DependencyRequest<TypedKey<?>>> dependencies = new ArrayList<>();
        private final Map<BeanLifecycleEventType, BeanLifecycleEventHandler<T>> eventHandlers =
                new EnumMap<>(BeanLifecycleEventType.class);

        public Builder<T> init(InitializationStrategy initializationStrategy) {
           this.initializationStrategy = initializationStrategy;
            return this;
        }

        public Builder<T> lazy() {
            return init(InitializationStrategy.LAZY);
        }

        public Builder<T> eager() {
            return init(InitializationStrategy.EAGER);
        }

        public Builder<T> scope(ScopeType scopeType) {
            this.scopeType = scopeType;
            return this;
        }

        public Builder<T> singleton() {
            return scope(ScopeType.SINGLETON);
        }

        public Builder<T> prototype() {
            return scope(ScopeType.PROTOTYPE);
        }

        public Builder<T> factory(BeanFactory<T> factory) {
            this.factory = factory;
            return this;
        }

        public Builder<T> eventHandler(BeanLifecycleEventType type, BeanLifecycleEventHandler<T> handler) {
            eventHandlers.put(type, handler);
            return this;
        }

        public Builder<T> dependsOn(
                TypedKey<?> dependencyKey,
                DependencyLink link,
                DependOrder order
        ) {
            dependencies.add(new SimpleDependencyRequest<>(dependencyKey, link, order));
            return this;
        }

        public FactoryBinding<T> build() throws BindingBuildException {
            try {

                Objects.requireNonNull(factory, "Binding bean factory must be non-null.");

                return new FactoryBinding<>(
                        key,
                        initializationStrategy,
                        scopeType,
                        factory,
                        dependencies,
                        eventHandlers
                );

            } catch (Exception ex) {
                throw new BindingBuildException("Failed to build a factory binding");
            }

        }

    }

    public static <T> Builder<T> builder(TypedKey<T> key) {
        return new Builder<>(key);
    }

    @Getter private final TypedKey<T> key;
    @Getter private final InitializationStrategy initializationStrategy;
    @Getter private final ScopeType scopeType;
    private final BeanFactory<T> factory;
    private final List<DependencyRequest<TypedKey<?>>> dependencies;
    private final Map<BeanLifecycleEventType, BeanLifecycleEventHandler<T>> eventHandlers;

    public FactoryBinding(
            TypedKey<T> key,
            InitializationStrategy initializationStrategy,
            ScopeType scopeType,
            BeanFactory<T> factory,
            List<DependencyRequest<TypedKey<?>>> dependencies,
            Map<BeanLifecycleEventType, BeanLifecycleEventHandler<T>> eventHandlers
    ) {
        Objects.requireNonNull(key, "Binding key must be non-null.");
        Objects.requireNonNull(scopeType, "Binding bean scope must be non-null.");
        Objects.requireNonNull(factory, "Binding bean factory must be non-null.");
        Objects.requireNonNull(dependencies, "Bean dependencies must be non-null.");
        Objects.requireNonNull(eventHandlers, "Bean event handlers must be non-null.");

        this.key = key;
        this.initializationStrategy = initializationStrategy;
        this.scopeType = scopeType;
        this.factory = factory;
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.eventHandlers = Map.copyOf(eventHandlers);
    }

    @Override
    public List<DependencyRequest<TypedKey<?>>> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    @Override
    public T create(BeanResolver resolver) throws BeanCreationException {
        return factory.create(resolver);
    }

    @Override
    public void invokeEventHandler(BeanLifecycleEventType type, T bean) throws BeanLifecycleEventHandleException {
        try {
            BeanLifecycleEventHandler<T> handler = eventHandlers.get(type);
            if (handler == null) return;
            handler.handle(bean);

        } catch (Exception ex) {
            throw new BeanLifecycleEventHandleException("Failed to invoke destroyer.", ex);

        }
    }

}
