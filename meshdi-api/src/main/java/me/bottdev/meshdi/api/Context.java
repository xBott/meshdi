package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.BeanAutowireException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;

import static me.bottdev.kern.commons.key.KeyUtils.key;

/// **Dependency Injection** Container in Modernium. Immutable.
public interface Context extends Disposable {

    ContextState state();

    String id();

    BindingContainer bindingManager();

    BeanLifecycleManager lifecycleManager();

    BeanResolver resolver();

    /// @throws BeanAutowireException if an error occurred while autowiring an object.
    <T> T autowire(Class<? extends T> implementation);

    default <T> T get(TypedKey<T> key) {
        return resolver().get(key);
    }

    default <T> T get(Class<T> type) {
        return get(key(type));
    }

    default <T> T get(Class<T> type, String qualifier) {
        return get(key(type, qualifier));
    }

    void start() throws ContextStartException;

}
