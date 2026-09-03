package me.bottdev.meshdi.api.bindings;

import lombok.NonNull;
import me.bottdev.meshdi.api.BeanLifecycleEventHandler;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.BindingBuilder;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;

/// A builder specifically for bindings that instantiate beans via their constructors.
public interface ConstructorBindingBuilder<T> extends BindingBuilder<T> {

    /// Sets the initialization strategy for this binding.
    ///
    /// @param initializationStrategy the strategy to use
    /// @return this builder instance
    ConstructorBindingBuilder<T> init(@NonNull InitializationStrategy initializationStrategy);

    /// Shorthand to set the initialization strategy to `LAZY`.
    ///
    /// @return this builder instance
    ConstructorBindingBuilder<T> lazy();

    /// Shorthand to set the initialization strategy to `EAGER`.
    ///
    /// @return this builder instance
    ConstructorBindingBuilder<T> eager();

    /// Sets the scope type for this binding.
    ///
    /// @param scopeType the scope type to use
    /// @return this builder instance
    ConstructorBindingBuilder<T> scope(@NonNull ScopeType scopeType);

    /// Shorthand to set the scope type to `SINGLETON`.
    ///
    /// @return this builder instance
    ConstructorBindingBuilder<T> singleton();

    /// Shorthand to set the scope type to `PROTOTYPE`.
    ///
    /// @return this builder instance
    ConstructorBindingBuilder<T> prototype();

    /// Specifies the concrete implementation class to instantiate for this binding.
    ///
    /// @param implementation the class to instantiate
    /// @return this builder instance
    ConstructorBindingBuilder<T> implementation(@NonNull Class<? extends T> implementation);

    /// Registers a lifecycle event handler for the instances created by this binding.
    ///
    /// @param type the lifecycle event type
    /// @param handler the handler to execute
    /// @return this builder instance
    ConstructorBindingBuilder<T> eventHandler(
            @NonNull BeanLifecycleEventType type,
            @NonNull BeanLifecycleEventHandler<T> handler
    );

}
