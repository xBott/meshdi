package me.bottdev.meshdi.core;

import me.bottdev.meshdi.api.BindingBuilder;
import me.bottdev.meshdi.api.ComponentDefinition;
import me.bottdev.meshdi.api.exceptions.ContextBootstrapException;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class SimpleContextBootstrap {

    public static SimpleContextBuilder bootstrap(ClassLoader classLoader) throws ContextBootstrapException {

        SimpleContextBuilder builder = new SimpleContextBuilder();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            for (ComponentDefinition<?> definition : ServiceLoader.load(ComponentDefinition.class, classLoader)) {
                BindingBuilder<?> bindingBuilder = definition.create();
                builder.binding(bindingBuilder);
            }

            return builder;
        } catch (ServiceConfigurationError ex) {
            throw new ContextBootstrapException("Failed to load bean definitions.", ex);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

    }

}
