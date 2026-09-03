package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;

import java.util.Optional;

/// Manages the creation, retrieval, and destruction of beans according to their scopes and bindings.
public interface BeanLifecycleManager extends Disposable {

    /// Checks if a bean associated with the specified binding currently exists in its scope.
    ///
    /// @param binding the binding to check
    /// @param <T> the type of the bean
    /// @return `true` if the bean exists, `false` otherwise
    <T> boolean contains(Binding<T> binding);

    /// Retrieves an existing bean associated with the specified binding.
    ///
    /// @param binding the binding identifying the bean
    /// @param <T> the type of the bean
    /// @return the existing bean instance
    <T> T get(Binding<T> binding);

    /// Attempts to find an existing bean associated with the specified binding.
    ///
    /// @param binding the binding identifying the bean
    /// @param <T> the type of the bean
    /// @return an [Optional] containing the bean if found, or empty otherwise
    <T> Optional<T> find(Binding<T> binding);

    /// Forces the creation of a new bean instance based on the specified binding.
    ///
    /// @param binding the binding containing instantiation metadata
    /// @param resolver the resolver to satisfy dependencies
    /// @param <T> the type of the bean
    /// @return the newly created bean instance
    /// @throws BeanLifecycleException if an error occurs during creation
    <T> T create(Binding<T> binding, BeanResolver resolver) throws BeanLifecycleException;

    /// Retrieves an existing bean or creates a new one if it does not yet exist.
    ///
    /// @param binding the binding defining the bean
    /// @param resolver the resolver to satisfy dependencies
    /// @param <T> the type of the bean
    /// @return the cached or newly created bean instance
    /// @throws BeanLifecycleException if an error occurs during creation or retrieval
    <T> T getOrCreate(Binding<T> binding, BeanResolver resolver) throws BeanLifecycleException;

}
