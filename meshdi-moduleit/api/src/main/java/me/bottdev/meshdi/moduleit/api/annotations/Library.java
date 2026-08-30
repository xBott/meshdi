package me.bottdev.meshdi.moduleit.api.annotations;

import me.bottdev.meshdi.moduleit.api.library.LibraryScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Library {
    String value();
    LibraryScope scope() default LibraryScope.SHARED;
}
