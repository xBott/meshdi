package me.bottdev.meshdi.core.bindings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.key.SimpleTypedKey;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.DependencyRequest;
import me.bottdev.kern.dependency.SimpleDependencyRequest;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.api.annotations.Inject;
import me.bottdev.meshdi.api.exceptions.BeanConstructorException;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;
import me.bottdev.meshdi.api.exceptions.BindingBuildException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;

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

        @SuppressWarnings("unchecked")
        private Constructor<T> findConstructor() throws BeanConstructorException {

            Constructor<?>[] constructors = implementation.getDeclaredConstructors();

            if (constructors.length == 1) {
                Constructor<T> ctor = (Constructor<T>) constructors[0];
                ctor.setAccessible(true);
                return ctor;

            } else {

                List<Constructor<?>> injected = Arrays.stream(constructors)
                        .filter(c -> c.isAnnotationPresent(Inject.class))
                        .toList();

                return switch (injected.size()) {
                    case 0 -> throw new BeanConstructorException(
                            implementation + " has multiple constructors without @Inject. " +
                                    "Annotate exactly one constructor with @Inject."
                    );
                    case 1 -> {
                        Constructor<T> ctor = (Constructor<T>) injected.getFirst();
                        ctor.setAccessible(true);
                        yield ctor;
                    }
                    default -> throw new BeanConstructorException(
                            implementation + " has multiple constructors annotated with @Inject. " +
                                    "Exactly one constructor must carry @Inject."
                    );
                };

            }

        }

        public ConstructorBinding<T> build() throws BindingBuildException {
            try {

                Objects.requireNonNull(scopeType, "Binding bean scope must be non-null.");

                if (implementation == null) implementation = key.type();
                Constructor<T> constructor = findConstructor();

                return new ConstructorBinding<>(
                        key,
                        initializationStrategy,
                        scopeType,
                        constructor,
                        eventHandlers
                );

            } catch (BeanConstructorException ex) {
                throw new BindingBuildException("Failed to find a suitable constructor for " + key.type(), ex);

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
    private final Constructor<? extends T> constructor;
    private final Map<BeanLifecycleEventType, BeanLifecycleEventHandler<T>> eventHandlers;

    private final List<DependencyRequest<TypedKey<?>>> dependencies = new ArrayList<>();

    public ConstructorBinding(
            TypedKey<T> key,
            InitializationStrategy initializationStrategy,
            ScopeType scopeType,
            Constructor<? extends T> constructor,
            Map<BeanLifecycleEventType, BeanLifecycleEventHandler<T>> eventHandlers
    ) {
        Objects.requireNonNull(key, "Binding key must be non-null.");
        Objects.requireNonNull(scopeType, "Binding bean scope must be non-null.");
        Objects.requireNonNull(constructor, "Bean constructor must be non-null.");
        Objects.requireNonNull(constructor, "Bean destroyer must be non-null.");
        Objects.requireNonNull(eventHandlers, "Bean event handlers must be non-null.");

        this.key = key;
        this.initializationStrategy = initializationStrategy;
        this.scopeType = scopeType;
        this.constructor = constructor;
        this.eventHandlers = Map.copyOf(eventHandlers);

        fetchDependenciesFromConstructor();
    }

    private DependencyRequest<TypedKey<?>> getParameterDependencyRequest(Parameter parameter) {
        Class<?> type = parameter.getType();
        if (!parameter.isAnnotationPresent(Dependency.class))
            return new SimpleDependencyRequest<>(SimpleTypedKey.of(type), DependencyLink.REQUIRED, DependOrder.AFTER);

        Dependency dependency = parameter.getAnnotation(Dependency.class);
        String qualifier = dependency.qualifier();
        DependencyLink link = dependency.link();
        DependOrder order = dependency.order();
        TypedKey<?> key = qualifier == null ?
                SimpleTypedKey.of(type) :
                SimpleTypedKey.of(type, qualifier);

        return new SimpleDependencyRequest<>(key, link, order);
    }

    private void fetchDependenciesFromConstructor() {
        Arrays.stream(constructor.getParameters())
                .forEach(parameter -> {
                    DependencyRequest<TypedKey<?>> request = getParameterDependencyRequest(parameter);
                    dependencies.add(request);
                });
    }

    @Override
    public List<DependencyRequest<TypedKey<?>>> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    @Override
    public T create(BeanResolver resolver) throws BeanCreationException {
        Object[] args = dependencies.stream()
                .map(request -> resolver.get((TypedKey<?>) request.key()))
                .toArray();
        try {
            return constructor.newInstance(args);

        } catch (InvocationTargetException ex) {
            throw new BeanCreationException("Constructor of a bean has thrown an exception.", ex);

        } catch (InstantiationException ex) {
            throw new BeanCreationException("Cannot create an instance of abstract class.", ex);

        } catch (IllegalAccessException ex) {
            throw new BeanCreationException("Constructor of a bean is not accessible.", ex);

        }
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
