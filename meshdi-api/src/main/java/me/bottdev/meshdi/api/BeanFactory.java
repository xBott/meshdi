package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.BeanCreationException;

/// A factory responsible for instantiating beans of type `T`.
@FunctionalInterface
public interface BeanFactory<T> {

    /// Creates a new instance of the bean, using the provided resolver to satisfy dependencies.
    ///
    /// @param resolver the resolver used to look up required dependencies
    /// @return the newly created bean instance
    /// @throws BeanCreationException if the bean cannot be created
    T create(BeanResolver resolver) throws BeanCreationException;

}
