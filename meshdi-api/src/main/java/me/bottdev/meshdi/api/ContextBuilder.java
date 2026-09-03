package me.bottdev.meshdi.api;

import lombok.NonNull;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.bindings.ConstructorBindingBuilder;
import me.bottdev.meshdi.api.bindings.FactoryBindingBuilder;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;

import java.util.Map;
import java.util.function.Consumer;

import static me.bottdev.kern.commons.key.KeyUtils.key;

/// Builder interface for creating and configuring a [Context].
public interface ContextBuilder {

    /// Sets the unique identifier for the context.
    ///
    /// @param id the unique string identifier
    /// @return this builder instance
    ContextBuilder id(@NonNull String id);

    /// Sets the lifecycle manager for the context.
    ///
    /// @param lifecycleManager the lifecycle manager to use
    /// @return this builder instance
    ContextBuilder lifecycleManager(@NonNull BeanLifecycleManager lifecycleManager);

    /// Registers a custom binding builder.
    ///
    /// @param bindingBuilder the binding builder to register
    /// @param <T> the type of the bean
    /// @return this builder instance
    <T> ContextBuilder binding(@NonNull BindingBuilder<T> bindingBuilder);

    /// Registers a factory binding.
    ///
    /// @param key the typed key identifying the bean
    /// @param config a consumer to configure the factory binding
    /// @param <T> the type of the bean
    /// @return this builder instance
    <T> ContextBuilder factory(
            @NonNull TypedKey<T> key,
            @NonNull Consumer<FactoryBindingBuilder<T>> config
    );

    /// Registers a constant value as a factory binding.
    ///
    /// @param key the typed key identifying the bean
    /// @param value the constant value to bind
    /// @param <T> the type of the bean
    /// @return this builder instance
    default <T> ContextBuilder constant(
            @NonNull TypedKey<T> key,
            @NonNull T value
    ) {
        return factory(key, config -> config.factory(_ -> value));
    }

    /// Registers a constructor binding.
    ///
    /// @param key the typed key identifying the bean
    /// @param config a consumer to configure the constructor binding
    /// @param <T> the type of the bean
    /// @return this builder instance
    <T> ContextBuilder construct(
            @NonNull TypedKey<T> key,
            @NonNull Consumer<ConstructorBindingBuilder<T>> config
    );

    /// Registers a constructor binding using a specific implementation class.
    ///
    /// @param key the typed key identifying the bean
    /// @param implementation the class to instantiate
    /// @param <T> the type of the bean
    /// @return this builder instance
    default <T> ContextBuilder construct(
            @NonNull TypedKey<T> key,
            @NonNull Class<? extends T> implementation
    ) {
        return construct(key, config -> config.implementation(implementation));
    }

    /// Registers a constructor binding using the class specified by the key type.
    ///
    /// @param type the class to bind and instantiate
    /// @param <T> the type of the bean
    /// @return this builder instance
    default <T> ContextBuilder construct(
            @NonNull Class<T> type
    ) {
        return construct(key(type), config -> config.implementation(type));
    }

    /// Retrieves all bindings currently registered in this builder.
    ///
    /// @return a map of registered binding builders, keyed by their typed keys
    Map<TypedKey<?>, BindingBuilder<?>> bindings();

    /// Merges the bindings from another context builder into this one.
    ///
    /// @param other the other context builder to merge from
    /// @return this builder instance
    ContextBuilder merge(@NonNull ContextBuilder other);

    /// Builds the [Context] with the configured bindings and managers.
    ///
    /// @return the fully initialized context
    /// @throws ContextBuildException if the context fails to build or dependency resolution fails
    Context build() throws ContextBuildException;

}
