package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;

import java.util.Optional;

public interface BeanLifecycleManager extends Disposable {

    <T> boolean contains(Binding<T> binding);

    <T> T get(Binding<T> binding);

    <T> Optional<T> find(Binding<T> binding);

    <T> T create(Binding<T> binding, BeanResolver resolver) throws BeanLifecycleException;

    <T> T getOrCreate(Binding<T> binding, BeanResolver resolver) throws BeanLifecycleException;

}
