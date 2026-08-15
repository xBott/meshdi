package me.bottdev.meshdi.core;

import me.bottdev.meshdi.api.BindingBuilder;
import me.bottdev.meshdi.api.ComponentDefinition;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;

import java.util.ServiceLoader;

public class SimpleContextBootstrap {

    public static SimpleContextBuilder bootstrap(ClassLoader classLoader) {

        SimpleContextBuilder builder = new SimpleContextBuilder();

        for (ComponentDefinition<?> definition : ServiceLoader.load(ComponentDefinition.class, classLoader)) {
            BindingBuilder<?> bindingBuilder = definition.create();
            builder.binding(bindingBuilder);
        }

        return builder;
    }

}
