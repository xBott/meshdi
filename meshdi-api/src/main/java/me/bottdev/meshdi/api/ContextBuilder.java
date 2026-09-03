package me.bottdev.meshdi.api;

import lombok.NonNull;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.bindings.ConstructorBindingBuilder;
import me.bottdev.meshdi.api.bindings.FactoryBindingBuilder;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;

import java.util.function.Consumer;

import static me.bottdev.kern.commons.key.KeyUtils.key;

public interface ContextBuilder {

    ContextBuilder id(@NonNull String id);

    ContextBuilder lifecycleManager(@NonNull BeanLifecycleManager lifecycleManager);

    <T> ContextBuilder binding(@NonNull BindingBuilder<T> bindingBuilder);

    <T> ContextBuilder factory(
            @NonNull TypedKey<T> key,
            @NonNull Consumer<FactoryBindingBuilder<T>> config
    );

    default <T> ContextBuilder constant(
            @NonNull TypedKey<T> key,
            @NonNull T value
    ) {
        return factory(key, config -> config.factory(_ -> value));
    }

    <T> ContextBuilder construct(
            @NonNull TypedKey<T> key,
            @NonNull Consumer<ConstructorBindingBuilder<T>> config
    );

    default <T> ContextBuilder construct(
            @NonNull TypedKey<T> key,
            @NonNull Class<? extends T> implementation
    ) {
        return construct(key, config -> config.implementation(implementation));
    }

    default <T> ContextBuilder construct(
            @NonNull Class<T> type
    ) {
        return construct(key(type), config -> config.implementation(type));
    }

    Context build() throws ContextBuildException;

}
