package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.BeanAutowireException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;

import static me.bottdev.kern.commons.key.KeyUtils.key;

/// **Dependency Injection** Container in Modernium. Immutable.
///
/// This interface represents the core of the DI container, providing access to beans,
/// their bindings, and the container's lifecycle state.
public interface Context extends Disposable {

    /// Retrieves the current lifecycle state of this context.
    ///
    /// @return the current [ContextState]
    ContextState state();

    /// Retrieves the unique identifier of this context.
    ///
    /// @return the string identifier
    String id();

    /// Retrieves the binding manager associated with this context.
    ///
    /// The binding manager provides access to all registered bean bindings.
    ///
    /// @return the [BindingContainer]
    BindingContainer bindingManager();

    /// Retrieves the lifecycle manager associated with this context.
    ///
    /// The lifecycle manager controls bean creation, destruction, and scope resolution.
    ///
    /// @return the [BeanLifecycleManager]
    BeanLifecycleManager lifecycleManager();

    /// Retrieves the bean resolver associated with this context.
    ///
    /// The resolver is used to look up and instantiate beans based on their keys.
    ///
    /// @return the [BeanResolver]
    BeanResolver resolver();

    /// Autowires a new instance of the specified implementation class.
    ///
    /// This method will inject all required dependencies into the newly created instance.
    /// The instance itself is not managed by the context's lifecycle unless explicitly registered.
    ///
    /// @param implementation the class to instantiate and autowire
    /// @param <T> the type of the class
    /// @return the fully initialized and autowired instance
    /// @throws BeanAutowireException if an error occurred while autowiring the object
    <T> T autowire(Class<? extends T> implementation);

    /// Retrieves a bean instance matching the specified key.
    ///
    /// @param key the typed key identifying the bean
    /// @param <T> the type of the bean
    /// @return the resolved bean instance
    default <T> T get(TypedKey<T> key) {
        return resolver().get(key);
    }

    /// Retrieves a bean instance matching the specified class type.
    ///
    /// @param type the class identifying the bean
    /// @param <T> the type of the bean
    /// @return the resolved bean instance
    default <T> T get(Class<T> type) {
        return get(key(type));
    }

    /// Retrieves a bean instance matching the specified class type and qualifier string.
    ///
    /// @param type the class identifying the bean
    /// @param qualifier the string qualifier distinguishing this bean from others of the same type
    /// @param <T> the type of the bean
    /// @return the resolved bean instance
    default <T> T get(Class<T> type, String qualifier) {
        return get(key(type, qualifier));
    }

    /// Starts this context, transitioning its state to `STARTED`.
    ///
    /// This usually triggers the initialization of eager singleton beans.
    ///
    /// @throws ContextStartException if the context fails to start
    void start() throws ContextStartException;

}
