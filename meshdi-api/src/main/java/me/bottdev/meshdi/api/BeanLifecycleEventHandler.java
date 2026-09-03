package me.bottdev.meshdi.api;

/// A functional interface for handling lifecycle events of a bean.
///
/// Handlers are executed when a specific [BeanLifecycleEventType] occurs,
/// such as post-construction or pre-destruction.
@FunctionalInterface
public interface BeanLifecycleEventHandler<T> {

    /// Processes the lifecycle event for the specified bean instance.
    ///
    /// @param bean the bean instance that triggered the event
    /// @throws Exception if an error occurs during event handling
    void handle(T bean) throws Exception;

}
