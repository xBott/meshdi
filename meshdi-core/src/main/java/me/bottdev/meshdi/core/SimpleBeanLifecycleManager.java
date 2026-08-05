package me.bottdev.meshdi.core;

import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.registry.Registry;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class SimpleBeanLifecycleManager implements BeanLifecycleManager {

    private final Registry<ScopeType, BeanScope> scopeRegistry;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private <T> Optional<BeanScope> getScopeByBinding(Binding<T> binding) {
        return scopeRegistry.find(binding.getScopeType());
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
                .map(BeanInstance::value)
                .orElse(null);
    }

    @Override
    public <T> Optional<T> find(Binding<T> binding) {
        return getScopeByBinding(binding)
                .map(scope -> scope.get(binding.getKey()))
                .map(BeanInstance::value);
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
            T value = scope.create(binding, resolver);
            binding.invokeEventHandler(BeanLifecycleEventType.POST_CONSTRUCT, value);

            return value;

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

            for (BeanScope scope : scopeRegistry.getAll()) {
                List<BeanInstance<?>> toDestroy = scope.getDestroyOrder();

                for (BeanInstance<?> instance : toDestroy) {
                    try {
                        destroy(instance);

                    } catch (BeanLifecycleEventHandleException ex) {
                        System.out.println("Failed to destroy " + instance + ". Reason: " + ex.getMessage());
                    }

                }
                scope.dispose();
            }

            scopeRegistry.clear();

        }
    }

    private <T> void destroy(BeanInstance<T> instance) throws BeanLifecycleEventHandleException {

        T value = instance.value();

        if (value instanceof Disposable disposable) {
            disposable.dispose();
        }

        instance.binding().invokeEventHandler(BeanLifecycleEventType.PRE_DESTROY, value);

    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
