package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;

import java.util.List;

public interface BeanScope extends Disposable {

    List<BeanInstance<?>> getDestroyOrder();

    <T> boolean contains(TypedKey<T> key);

    <T> BeanInstance<T> get(TypedKey<T> key);

    <T> T create(Binding<T> binding, BeanResolver resolver) throws BeanCreationException;

}
