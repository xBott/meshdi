package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependentContainer;

import java.util.List;

/// A container that holds all registered bindings in a context.
///
/// This container also tracks dependencies between bindings to establish a proper
/// initialization order.
public interface BindingContainer extends DependentContainer<TypedKey<?>, Binding<?>>, Disposable {

    /// Checks if a binding exists for the specified key.
    ///
    /// @param key the typed key to check
    /// @param <T> the type of the bean
    /// @return `true` if a binding is registered, `false` otherwise
    <T> boolean containsBinding(TypedKey<T> key);

    /// Retrieves the binding associated with the specified key.
    ///
    /// @param key the typed key identifying the binding
    /// @param <T> the type of the bean
    /// @return the registered binding, or `null` if not found
    <T> Binding<T> getBinding(TypedKey<T> key);

    /// Retrieves a list of all registered bindings.
    ///
    /// @return the list of bindings
    List<Binding<?>> getBindings();

    /// Returns the total number of registered bindings.
    ///
    /// @return the binding count
    int size();

}
