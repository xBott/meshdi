package me.bottdev.meshdi.api;

/// Defines the supported scoping strategies for beans.
public enum ScopeType {
    /// A single instance is created and shared across the entire context.
    SINGLETON,

    /// A new instance is created every time the bean is requested.
    PROTOTYPE
}
