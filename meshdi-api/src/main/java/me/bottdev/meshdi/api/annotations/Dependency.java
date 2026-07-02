package me.bottdev.meshdi.api.annotations;

import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface Dependency {
    String qualifier() default "";
    DependencyLink link() default DependencyLink.REQUIRED;
    DependOrder order() default DependOrder.AFTER;
}
