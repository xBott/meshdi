package me.bottdev.meshdi.api;

/// A wrapper record that associates a live bean instance with the binding used to create it.
///
/// @param binding the binding metadata
/// @param value the actual instantiated bean
/// @param <T> the type of the bean
public record BeanInstance<T>(Binding<T> binding, T value) {}
