package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;

import java.util.Optional;

/// Resolves and retrieves bean instances from the DI container.
public interface BeanResolver {

    /// Checks if a bean matching the specified key exists in the container.
    ///
    /// @param key the typed key to check for
    /// @param <T> the type of the bean
    /// @return `true` if the bean exists and can be resolved, `false` otherwise
    <T> boolean contains(TypedKey<T> key);

    /// Retrieves a bean instance matching the specified key.
    ///
    /// @param key the typed key identifying the bean
    /// @param <T> the type of the bean
    /// @return the resolved bean instance, never `null`
    /// @throws BeanResolvationException if no binding exists for the key
    /// or the bean cannot be created. Use [#find(TypedKey)] for beans that may be absent.
    <T> T get(TypedKey<T> key);

    /// Attempts to retrieve a bean instance, returning an empty [Optional] if not found.
    ///
    /// @param key the typed key identifying the bean
    /// @param <T> the type of the bean
    /// @return an [Optional] containing the bean if found, or empty otherwise
    <T> Optional<T> find(TypedKey<T> key);

    /// Retrieves the [Binding] associated with the specified key.
    ///
    /// @param key the typed key identifying the binding
    /// @param <T> the type of the bean
    /// @return the associated binding, or `null` if no binding exists for the key
    <T> Binding<T> getBinding(TypedKey<T> key);

}
