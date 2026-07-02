package me.bottdev.meshdi.core.bindings;

import io.gitlab.modernium.api.commons.dependency.DependOrder;
import io.gitlab.modernium.api.commons.dependency.DependRequirement;
import io.gitlab.modernium.api.commons.dependency.DependencyRequest;
import io.gitlab.modernium.api.commons.key.TypedKey;
import io.gitlab.modernium.api.di.*;
import io.gitlab.modernium.api.di.exceptions.BeanCreationException;
import io.gitlab.modernium.api.di.exceptions.BindingBuildException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class FactoryBinding<T> implements Binding<T> {

    @RequiredArgsConstructor
    public static class Builder<T> implements BindingBuilder<T> {

        @Getter
        private final TypedKey<T> key;
        private InitializationStrategy initializationStrategy = InitializationStrategy.LAZY;
        private ScopeType scopeType = ScopeType.SINGLETON;
        private BeanFactory<T> factory = null;
        private final List<DependencyRequest> dependencies = new ArrayList<>();

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

        public Builder<T> dependsOn(
                TypedKey<?> dependencyKey,
                DependRequirement requirement,
                DependOrder order
        ) {
            dependencies.add(new DependencyRequest(dependencyKey, requirement, order));
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
                        dependencies
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

    private final List<DependencyRequest> dependencies;

    public FactoryBinding(
            TypedKey<T> key,
            InitializationStrategy initializationStrategy,
            ScopeType scopeType,
            BeanFactory<T> factory,
            List<DependencyRequest> dependencies
    ) {
        Objects.requireNonNull(key, "Binding key must be non-null.");
        Objects.requireNonNull(scopeType, "Binding bean scope must be non-null.");
        Objects.requireNonNull(factory, "Binding bean factory must be non-null.");

        this.key = key;
        this.initializationStrategy = initializationStrategy;
        this.scopeType = scopeType;
        this.factory = factory;
        this.dependencies = dependencies;
    }

    @Override
    public List<DependencyRequest> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    @Override
    public T create(BeanResolver resolver) throws BeanCreationException {
        return factory.create(resolver);
    }

}
