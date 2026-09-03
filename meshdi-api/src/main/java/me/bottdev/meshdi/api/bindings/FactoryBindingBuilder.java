package me.bottdev.meshdi.api.bindings;

import lombok.NonNull;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.meshdi.api.BeanFactory;
import me.bottdev.meshdi.api.BeanLifecycleEventHandler;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.BindingBuilder;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;

/// A builder specifically for bindings that instantiate beans using a custom [BeanFactory].
public interface FactoryBindingBuilder<T> extends BindingBuilder<T> {

    /// Sets the initialization strategy for this binding.
    ///
    /// @param initializationStrategy the strategy to use
    /// @return this builder instance
    FactoryBindingBuilder<T> init(@NonNull InitializationStrategy initializationStrategy);

    /// Shorthand to set the initialization strategy to `LAZY`.
    ///
    /// @return this builder instance
    FactoryBindingBuilder<T> lazy();

    /// Shorthand to set the initialization strategy to `EAGER`.
    ///
    /// @return this builder instance
    FactoryBindingBuilder<T> eager();

    /// Sets the scope type for this binding.
    ///
    /// @param scopeType the scope type to use
    /// @return this builder instance
    FactoryBindingBuilder<T> scope(@NonNull ScopeType scopeType);

    /// Shorthand to set the scope type to `SINGLETON`.
    ///
    /// @return this builder instance
    FactoryBindingBuilder<T> singleton();

    /// Shorthand to set the scope type to `PROTOTYPE`.
    ///
    /// @return this builder instance
    FactoryBindingBuilder<T> prototype();

    /// Adds a dependency requirement for this binding.
    ///
    /// @param dependencyKey the typed key of the dependency
    /// @param link the dependency link requirement (e.g., REQUIRED, OPTIONAL)
    /// @param order the initialization order relative to the dependency (e.g., AFTER, BEFORE)
    /// @return this builder instance
    FactoryBindingBuilder<T> dependsOn(
            @NonNull TypedKey<?> dependencyKey,
            @NonNull DependencyLink link,
            @NonNull DependOrder order
    );

    /// Adds a dependency requirement for this binding with a default order of `AFTER`.
    ///
    /// @param dependencyKey the typed key of the dependency
    /// @param link the dependency link requirement
    /// @return this builder instance
    default FactoryBindingBuilder<T> dependsOn(
            @NonNull TypedKey<?> dependencyKey,
            @NonNull DependencyLink link
    ) {
        return dependsOn(dependencyKey, link, DependOrder.AFTER);
    }

    /// Adds a required dependency for this binding with a default order of `AFTER`.
    ///
    /// @param dependencyKey the typed key of the dependency
    /// @return this builder instance
    default FactoryBindingBuilder<T> dependsOn(@NonNull TypedKey<?> dependencyKey) {
        return dependsOn(dependencyKey, DependencyLink.REQUIRED, DependOrder.AFTER);
    }

    /// Specifies the custom factory used to instantiate the bean.
    ///
    /// @param factory the bean factory
    /// @return this builder instance
    FactoryBindingBuilder<T> factory(@NonNull BeanFactory<T> factory);

    /// Registers a lifecycle event handler for the instances created by this binding.
    ///
    /// @param type the lifecycle event type
    /// @param handler the handler to execute
    /// @return this builder instance
    FactoryBindingBuilder<T> eventHandler(
            @NonNull BeanLifecycleEventType type,
            @NonNull BeanLifecycleEventHandler<T> handler
    );

}
