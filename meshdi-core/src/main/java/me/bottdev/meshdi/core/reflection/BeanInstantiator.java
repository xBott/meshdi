package me.bottdev.meshdi.core.reflection;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependencyRequest;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;

import java.lang.reflect.Constructor;
import java.util.List;

public interface BeanInstantiator<T> {
    Constructor<T> getConstructor();
    List<DependencyRequest<TypedKey<?>>> getDependencies();
    T instantiate(BeanResolver resolver) throws BeanCreationException;
}
