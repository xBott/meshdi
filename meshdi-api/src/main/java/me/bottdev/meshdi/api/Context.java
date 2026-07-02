package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.meshdi.api.exceptions.ContextStartException;

/// **Dependency Injection** Container in Modernium. Immutable.
public interface Context extends Disposable {

    ContextState getState();

    String getId();

    BindingContainer getBindingContainer();

    BeanLifecycleManager getLifecycleManager();

    BeanResolver getResolver();

    void start() throws ContextStartException;

}
