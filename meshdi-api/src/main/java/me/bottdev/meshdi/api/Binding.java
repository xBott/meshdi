package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependencyAware;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;

public interface Binding<T> extends DependencyAware<TypedKey<?>> {

    TypedKey<T> getKey();

    InitializationStrategy getInitializationStrategy();

    ScopeType getScopeType();

    default boolean hasDependencies() {
        return !getDependencies().isEmpty();
    }

    /// Method that creates a real object from binding
    T create(BeanResolver resolver) throws BeanCreationException;

    @Override
    default TypedKey<?> dependencyKey() {
        return getKey();
    }

}
