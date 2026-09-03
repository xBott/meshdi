package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependencyAware;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;

/// Represents a configured definition of how to create and manage a specific bean.
///
/// A binding contains the metadata required to instantiate a bean (such as its key,
/// initialization strategy, scope, and dependencies) and handles its lifecycle events.
public interface Binding<T> extends DependencyAware<TypedKey<?>> {

    /// Retrieves the unique key identifying this binding.
    ///
    /// @return the typed key
    TypedKey<T> getKey();

    /// Retrieves the initialization strategy for this binding.
    ///
    /// @return the initialization strategy (e.g., EAGER, LAZY)
    InitializationStrategy getInitializationStrategy();

    /// Retrieves the scope type assigned to this binding.
    ///
    /// @return the scope type
    ScopeType getScopeType();

    /// Checks if this binding relies on any dependencies.
    ///
    /// @return `true` if there are dependencies, `false` otherwise
    default boolean hasDependencies() {
        return !getDependencies().isEmpty();
    }

    /// Creates a real object instance from this binding.
    ///
    /// @param resolver the resolver used to satisfy dependencies during creation
    /// @return the instantiated bean
    /// @throws BeanCreationException if an error occurs during instantiation
    T create(BeanResolver resolver) throws BeanCreationException;

    /// Invokes a specific lifecycle event handler for the given bean instance.
    ///
    /// @param type the lifecycle event type (e.g., POST_CONSTRUCT, PRE_DESTROY)
    /// @param bean the bean instance on which the event occurred
    /// @throws BeanLifecycleEventHandleException if the handler fails to execute
    void invokeEventHandler(BeanLifecycleEventType type, T bean) throws BeanLifecycleEventHandleException;

    @Override
    default TypedKey<?> dependencyKey() {
        return getKey();
    }

}
