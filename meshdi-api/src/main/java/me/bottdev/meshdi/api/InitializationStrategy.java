package me.bottdev.meshdi.api;

/// Defines when a bean should be instantiated by the container.
public enum InitializationStrategy {
    /// The bean is created only when it is first requested (either directly or as a dependency).
    LAZY,

    /// The bean is created immediately upon context startup. Usually applies only to singletons.
    EAGER
}
