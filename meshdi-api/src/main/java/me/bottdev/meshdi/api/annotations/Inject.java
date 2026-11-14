package me.bottdev.meshdi.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a constructor as the injection point for the DI container.
///
/// The container uses the annotated constructor to instantiate the component,
/// automatically resolving its parameters. Field injection is not supported.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface Inject {}
