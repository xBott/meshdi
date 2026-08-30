package me.bottdev.meshdi.core.reflection;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.exceptions.BeanConstructorException;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

public class RecursiveBeanInstantiator<T> extends AbstractBeanInstantiator<T> {

    private final Function<Class<?>, Object> autowireFunction;

    public RecursiveBeanInstantiator(Class<? extends T> implementation, Function<Class<?>, Object> autowireFunction) throws BeanConstructorException {
        super(implementation);
        this.autowireFunction = autowireFunction;
    }

    @Override
    public T instantiate(BeanResolver resolver) throws BeanCreationException {
        Object[] args = dependencies.stream()
                .map(request -> {
                    TypedKey<?> key = (TypedKey<?>) request.key();
                    if (resolver.contains(key)) {
                        return resolver.get(key);
                    } else {
                        return autowireFunction.apply(key.type());
                    }
                })
                .toArray();
        try {
            return constructor.newInstance(args);

        } catch (InvocationTargetException ex) {
            throw new BeanCreationException("Constructor of a bean has thrown an exception.", ex);

        } catch (InstantiationException ex) {
            throw new BeanCreationException("Cannot create an instance of abstract class.", ex);

        } catch (IllegalAccessException ex) {
            throw new BeanCreationException("Constructor of a bean is not accessible.", ex);
        }
    }
}
