package me.bottdev.meshdi.moduleit.api.annotations;

import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Dependency declaration in [Module] annotation.
/// Supports version, dependency order and link.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface DependsOn {
    String id();
    String version() default "*";
    DependOrder order() default DependOrder.AFTER;
    DependencyLink link() default DependencyLink.REQUIRED;
}
