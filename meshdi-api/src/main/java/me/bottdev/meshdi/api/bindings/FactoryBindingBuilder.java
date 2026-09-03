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

public interface FactoryBindingBuilder<T> extends BindingBuilder<T> {

    FactoryBindingBuilder<T> init(@NonNull InitializationStrategy initializationStrategy);
    FactoryBindingBuilder<T> lazy();
    FactoryBindingBuilder<T> eager();

    FactoryBindingBuilder<T> scope(@NonNull ScopeType scopeType);
    FactoryBindingBuilder<T> singleton();
    FactoryBindingBuilder<T> prototype();

    FactoryBindingBuilder<T> dependsOn(
            @NonNull TypedKey<?> dependencyKey,
            @NonNull DependencyLink link,
            @NonNull DependOrder order
    );
    default FactoryBindingBuilder<T> dependsOn(
            @NonNull TypedKey<?> dependencyKey,
            @NonNull DependencyLink link
    ) {
        return dependsOn(dependencyKey, link, DependOrder.AFTER);
    }
    default FactoryBindingBuilder<T> dependsOn(@NonNull TypedKey<?> dependencyKey) {
        return dependsOn(dependencyKey, DependencyLink.REQUIRED, DependOrder.AFTER);
    }

    FactoryBindingBuilder<T> factory(@NonNull BeanFactory<T> factory);

    FactoryBindingBuilder<T> eventHandler(
            @NonNull BeanLifecycleEventType type,
            @NonNull BeanLifecycleEventHandler<T> handler
    );

}
