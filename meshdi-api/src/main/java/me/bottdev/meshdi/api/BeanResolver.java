package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.key.TypedKey;

import java.util.Optional;

public interface BeanResolver {

    <T> boolean contains(TypedKey<T> key);

    <T> T get(TypedKey<T> key);

    <T> Optional<T> find(TypedKey<T> key);

    <T> Binding<T> getBinding(TypedKey<T> key);

}
