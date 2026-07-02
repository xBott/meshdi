package me.bottdev.meshdi.api.annotations;

import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Component {
    String qualifier() default "";
    InitializationStrategy init() default InitializationStrategy.LAZY;
    ScopeType scope() default ScopeType.SINGLETON;
}
