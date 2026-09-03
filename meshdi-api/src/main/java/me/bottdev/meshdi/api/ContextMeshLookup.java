package me.bottdev.meshdi.api;

/// A wrapper containing the result of a cross-context bean lookup within a [ContextMesh].
///
/// @param owner the context that owns the resolved bean
/// @param binding the binding of the resolved bean
/// @param <T> the type of the bean
public record ContextMeshLookup<T>(
        Context owner,
        Binding<T> binding
) {}
