package me.bottdev.meshdi.moduleit.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Annotation that declares a module.
/// Used for [me.bottdev.meshdi.moduleit.api.ModuleDescriptor] generation.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Module {

    String id();
    String version();
    String apiVersion() default "*";
    DependsOn[] dependencies() default {};
    String[] exports() default {};
    Repository[] repositories() default {};
    Library[] libraries() default {};

}
