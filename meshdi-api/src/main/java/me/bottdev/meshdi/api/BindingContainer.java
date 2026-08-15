package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependentContainer;

import java.util.List;

public interface BindingContainer extends DependentContainer<TypedKey<?>, Binding<?>>, Disposable {

    <T> boolean containsBinding(TypedKey<T> key);

    <T> Binding<T> getBinding(TypedKey<T> key);

    List<Binding<?>> getBindings();

    int size();

}
