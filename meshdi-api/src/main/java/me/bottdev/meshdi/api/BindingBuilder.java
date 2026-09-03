package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.BindingBuildException;

/// A builder for constructing [Binding] instances.
public interface BindingBuilder<T> {

    /// Retrieves the unique key for the binding being built.
    ///
    /// @return the typed key
    TypedKey<T> getKey();

    /// Constructs the final [Binding] instance.
    ///
    /// @return the built binding
    /// @throws BindingBuildException if the binding configuration is invalid or incomplete
    Binding<T> build() throws BindingBuildException;

}
