package me.bottdev.meshdi.api;

/// Represents the different stages in a bean's lifecycle where events can be intercepted.
public enum BeanLifecycleEventType {
    /// Triggered immediately after the bean has been instantiated and its dependencies injected.
    POST_CONSTRUCT,

    /// Triggered just before the bean is destroyed and removed from the container.
    PRE_DESTROY
}
