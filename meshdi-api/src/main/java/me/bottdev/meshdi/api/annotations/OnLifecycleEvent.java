package me.bottdev.meshdi.api.annotations;

import me.bottdev.meshdi.api.BeanLifecycleEventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnLifecycleEvent {

    BeanLifecycleEventType value() default BeanLifecycleEventType.POST_CONSTRUCT;

}
