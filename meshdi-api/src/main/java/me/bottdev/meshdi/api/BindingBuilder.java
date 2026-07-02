package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.BindingBuildException;

public interface BindingBuilder<T> {

    TypedKey<T> getKey();

    Binding<T> build() throws BindingBuildException;

}
