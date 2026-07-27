package me.bottdev.meshdi.core;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.exceptions.DisposeException;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.annotations.PreDestroy;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class SimpleBeanLifecycleManager implements BeanLifecycleManager {

    //private final Registry<ScopeType, BeanScope> scopeRegistry;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private <T> Optional<BeanScope> getScopeByBinding(Binding<T> binding) {
        return Optional.empty();
        //return scopeRegistry.find(binding.getScopeType());
    }

    @Override
    public <T> boolean contains(Binding<T> binding) {
        return getScopeByBinding(binding)
                .map(scope -> scope.contains(binding.getKey()))
                .orElse(false);
    }

    @Override
    public <T> T get(Binding<T> binding) {
        return getScopeByBinding(binding)
                .map(scope -> scope.get(binding.getKey()))
                .orElse(null);
    }

    @Override
    public <T> Optional<T> find(Binding<T> binding) {
        return getScopeByBinding(binding)
                .map(scope -> scope.get(binding.getKey()));
    }

    @Override
    public <T> T create(Binding<T> binding, BeanResolver resolver)
            throws BeanLifecycleException {

        if (contains(binding)) throw new BeanLifecycleException(
                "Bean " + binding.getKey() + " is already created."
        );

        try {

            BeanScope scope = getScopeByBinding(binding)
                    .orElseThrow(() -> new IllegalArgumentException("Bean Scope does not exist."));
            return scope.create(binding, resolver);

        } catch (BeanResolvationException ex) {
            throw new BeanLifecycleException(
                    "Failed to resolve dependency for bean " + binding.getKey() + ".", ex
            );
        } catch (BeanCreationException ex) {
            throw new BeanLifecycleException(
                    "Failed to create bean " + binding.getKey() + ".", ex
            );
        } catch (IllegalArgumentException ex) {
            throw new BeanLifecycleException(
                    "Failed to find bean scope of " + binding.getKey() + ".", ex
            );
        }
    }

    @Override
    public <T> T getOrCreate(Binding<T> binding, BeanResolver resolver) throws BeanLifecycleException {

        if (contains(binding)) {
            return get(binding);
        }
        return create(binding, resolver);
    }



    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {

//            for (BeanScope scope : scopeRegistry.getAll()) {
//                List<Object> toDestroy = scope.getDestroyOrder();
//
//                for (Object value : toDestroy) {
//                    try {
//                        destroy(value);
//
//                    } catch (DisposeException ex) {
//                        System.out.println("Failed to destroy " + value + ". Reason: " + ex.getMessage());
//                    }
//
//                }
//                scope.dispose();
//            }
//
//            scopeRegistry.clear();

        }
    }

    private void destroy(Object value) throws DisposeException {

        if (value instanceof Disposable disposable) {
            try {
                disposable.dispose();

            } catch (DisposeException ex) {
                throw new DisposeException(value, "Failed to invoke dispose() method of " + value + ".", ex);
            }

        }

        try {
            invokePreDestroy(value);

        } catch (DisposeException ex) {
            throw new DisposeException(value, "Failed to invoke @PreDestroy methods of " + value + ".", ex);
        }

    }

    private void invokePreDestroy(Object value) throws DisposeException {

        Method[] methods = value.getClass().getDeclaredMethods();

        for (Method method : methods) {

            if (!method.isAnnotationPresent(PreDestroy.class)) continue;

            String name = method.getName();
            if (method.getParameters().length != 0)
                throw new DisposeException(this,
                        "@PreDestroy method " + name + " of " + value.getClass().getName() + " can not have parameters.");

            try {
                method.setAccessible(true);
                method.invoke(value);

            } catch (Exception ex) {
                throw new DisposeException(this,
                        "Failed to invoke @PreDestroy method " + name + " of " + value.getClass().getName(), ex);

            }

        }

    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
