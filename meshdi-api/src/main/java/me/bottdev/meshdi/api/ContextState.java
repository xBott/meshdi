package me.bottdev.meshdi.api;

/// Represents the lifecycle states of a [Context].
public enum ContextState {
    /// The context is created but has not been started yet.
    CREATED,
    
    /// The context is fully initialized and active.
    STARTED,
    
    /// The context has been destroyed and is no longer usable.
    DISPOSED
}
