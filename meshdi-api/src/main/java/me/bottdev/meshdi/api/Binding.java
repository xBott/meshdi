package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependencyAware;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;

public interface Binding<T> extends DependencyAware<TypedKey<?>> {

    TypedKey<T> getKey();

    InitializationStrategy getInitializationStrategy();

    ScopeType getScopeType();

    default boolean hasDependencies() {
        return !getDependencies().isEmpty();
    }

    /// Method that creates a real object from binding
    T create(BeanResolver resolver) throws BeanCreationException;

    /// Method that invoke a specific handler according to the lifecycle event of the bean
    void invokeEventHandler(BeanLifecycleEventType type, T bean) throws BeanLifecycleEventHandleException;

    @Override
    default TypedKey<?> dependencyKey() {
        return getKey();
    }

}
