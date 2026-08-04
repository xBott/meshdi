package me.bottdev.meshdi.api;

public interface ComponentDefinition<T> {

    BindingBuilder<T> create();

}
