package me.bottdev.meshdi.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a constructor or field as an injection point for the DI container.
///
/// If applied to a constructor, the container will use it to instantiate the component,
/// automatically resolving its parameters. If applied to a field, the container will
/// inject the resolved dependency into it post-construction.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Inject {}
