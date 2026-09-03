package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;

import java.util.List;

/// Defines the lifecycle boundaries and visibility of beans within the DI container.
///
/// Typical implementations include Singleton (one instance per context) and
/// Prototype (new instance per request).
public interface BeanScope extends Disposable {

    /// Retrieves the list of managed bean instances in the order they should be destroyed.
    ///
    /// @return the list of bean instances
    List<BeanInstance<?>> getDestroyOrder();

    /// Checks whether an instance associated with the specified key exists in this scope.
    ///
    /// @param key the typed key
    /// @param <T> the type of the bean
    /// @return `true` if the instance exists, `false` otherwise
    <T> boolean contains(TypedKey<T> key);

    /// Retrieves the cached [BeanInstance] associated with the specified key.
    ///
    /// @param key the typed key
    /// @param <T> the type of the bean
    /// @return the bean instance metadata wrapper
    <T> BeanInstance<T> get(TypedKey<T> key);

    /// Creates or retrieves a bean instance according to the rules of this scope.
    ///
    /// @param binding the binding defining the bean creation rules
    /// @param resolver the resolver to satisfy dependencies
    /// @param <T> the type of the bean
    /// @return the instantiated or cached bean
    /// @throws BeanCreationException if an error occurs during instantiation
    <T> T create(Binding<T> binding, BeanResolver resolver) throws BeanCreationException;

}
