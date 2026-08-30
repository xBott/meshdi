package me.bottdev.meshdi.core.bindings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependencyRequest;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;
import me.bottdev.meshdi.api.exceptions.BindingBuildException;
import me.bottdev.meshdi.core.reflection.BeanInstantiator;
import me.bottdev.meshdi.core.reflection.SimpleBeanInstantiator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConstructorBinding<T> implements Binding<T> {

    @RequiredArgsConstructor
    public static class Builder<T> implements BindingBuilder<T> {

        @Getter
        private final TypedKey<T> key;
        private InitializationStrategy initializationStrategy = InitializationStrategy.LAZY;
        private ScopeType scopeType = ScopeType.SINGLETON;
        private Class<? extends T> implementation;
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

        public Builder<T> implementation(Class<? extends T> implementation) {
            this.implementation = implementation;
            return this;
        }

        public ConstructorBinding.Builder<T> eventHandler(BeanLifecycleEventType type, BeanLifecycleEventHandler<T> handler) {
            eventHandlers.put(type, handler);
            return this;
        }

        public ConstructorBinding<T> build() throws BindingBuildException {
            try {
                Objects.requireNonNull(scopeType, "Binding bean scope must be non-null.");

                if (implementation == null) implementation = key.type();
                
                BeanInstantiator<T> instantiator = new SimpleBeanInstantiator<>(implementation);

                return new ConstructorBinding<>(
                        key,
                        initializationStrategy,
                        scopeType,
                        instantiator,
                        eventHandlers
                );

            } catch (Exception ex) {
                throw new BindingBuildException("Failed to build a constructor binding", ex);
            }
        }
    }

    public static <T> Builder<T> builder(TypedKey<T> key) {
        return new Builder<>(key);
    }

    @Getter private final TypedKey<T> key;
    @Getter private final InitializationStrategy initializationStrategy;
    @Getter private final ScopeType scopeType;
    private final BeanInstantiator<T> instantiator;
    private final Map<BeanLifecycleEventType, BeanLifecycleEventHandler<T>> eventHandlers;

    public ConstructorBinding(
            TypedKey<T> key,
            InitializationStrategy initializationStrategy,
            ScopeType scopeType,
            BeanInstantiator<T> instantiator,
            Map<BeanLifecycleEventType, BeanLifecycleEventHandler<T>> eventHandlers
    ) {
        Objects.requireNonNull(key, "Binding key must be non-null.");
        Objects.requireNonNull(scopeType, "Binding bean scope must be non-null.");
        Objects.requireNonNull(instantiator, "Bean instantiator must be non-null.");
        Objects.requireNonNull(eventHandlers, "Bean event handlers must be non-null.");

        this.key = key;
        this.initializationStrategy = initializationStrategy;
        this.scopeType = scopeType;
        this.instantiator = instantiator;
        this.eventHandlers = Map.copyOf(eventHandlers);
    }

    @Override
    public List<DependencyRequest<TypedKey<?>>> getDependencies() {
        return instantiator.getDependencies();
    }

    @Override
    public T create(BeanResolver resolver) throws BeanCreationException {
        return instantiator.instantiate(resolver);
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
