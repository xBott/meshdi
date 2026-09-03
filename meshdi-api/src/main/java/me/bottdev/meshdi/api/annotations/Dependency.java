package me.bottdev.meshdi.api.annotations;

import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Provides additional configuration for an injected dependency.
///
/// Can be applied to a constructor parameter or an injected field to specify
/// qualifiers, requirement links (e.g., required or optional), and initialization order.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface Dependency {

    /// An optional string qualifier to identify the specific bean if multiple
    /// beans of the same type exist.
    String qualifier() default "";

    /// Specifies whether the dependency is required or optional.
    /// Defaults to [DependencyLink#REQUIRED].
    DependencyLink link() default DependencyLink.REQUIRED;

    /// Specifies the initialization order relative to the dependency.
    /// Defaults to [DependOrder#AFTER] (i.e., this component is initialized after the dependency).
    DependOrder order() default DependOrder.AFTER;
}
