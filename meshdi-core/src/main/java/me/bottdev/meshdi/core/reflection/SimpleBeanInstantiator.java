package me.bottdev.meshdi.core.reflection;

import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.exceptions.BeanConstructorException;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;

import java.lang.reflect.InvocationTargetException;

public class SimpleBeanInstantiator<T> extends AbstractBeanInstantiator<T> {

    public SimpleBeanInstantiator(Class<? extends T> implementation) throws BeanConstructorException {
        super(implementation);
    }

    @Override
    public T instantiate(BeanResolver resolver) throws BeanCreationException {
        Object[] args = dependencies.stream()
                .map(request -> resolver.get((TypedKey<?>) request.key()))
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
