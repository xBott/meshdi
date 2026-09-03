package me.bottdev.meshdi.api.annotations;

import me.bottdev.meshdi.api.BeanLifecycleEventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a method as an event handler for a specific stage of a bean's lifecycle.
///
/// The annotated method must take no arguments and will be invoked automatically
/// by the DI container when the specified event occurs.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnLifecycleEvent {

    /// Specifies the lifecycle event type that triggers the method execution.
    /// Defaults to [BeanLifecycleEventType#POST_CONSTRUCT].
    BeanLifecycleEventType value() default BeanLifecycleEventType.POST_CONSTRUCT;

}
