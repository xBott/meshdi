package me.bottdev.meshdi.api.bindings;

import lombok.NonNull;
import me.bottdev.meshdi.api.BeanLifecycleEventHandler;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.BindingBuilder;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;

public interface ConstructorBindingBuilder<T> extends BindingBuilder<T> {

    ConstructorBindingBuilder<T> init(@NonNull InitializationStrategy initializationStrategy);
    ConstructorBindingBuilder<T> lazy();
    ConstructorBindingBuilder<T> eager();

    ConstructorBindingBuilder<T> scope(@NonNull ScopeType scopeType);
    ConstructorBindingBuilder<T> singleton();
    ConstructorBindingBuilder<T> prototype();

    ConstructorBindingBuilder<T> implementation(@NonNull Class<? extends T> implementation);

    ConstructorBindingBuilder<T> eventHandler(
            @NonNull BeanLifecycleEventType type,
            @NonNull BeanLifecycleEventHandler<T> handler
    );

}
