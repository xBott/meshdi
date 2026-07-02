package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.BeanCreationException;

@FunctionalInterface
public interface BeanFactory<T> {

    T create(BeanResolver resolver) throws BeanCreationException;

}
