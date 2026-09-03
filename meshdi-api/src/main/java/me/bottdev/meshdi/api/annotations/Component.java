package me.bottdev.meshdi.api.annotations;

import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Indicates that an annotated class is a "component" and should be automatically
/// discovered and registered into the DI container by the annotation processor.
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Component {

    /// An optional string qualifier to distinguish between multiple beans of the same type.
    /// Defaults to an empty string.
    String qualifier() default "";

    /// Specifies when the container should instantiate this component.
    /// Defaults to [InitializationStrategy#LAZY].
    InitializationStrategy init() default InitializationStrategy.LAZY;

    /// Specifies the scope of the component, determining how instances are shared.
    /// Defaults to [ScopeType#SINGLETON].
    ScopeType scope() default ScopeType.SINGLETON;
}
