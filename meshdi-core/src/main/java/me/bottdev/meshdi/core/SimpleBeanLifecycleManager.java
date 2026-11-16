package me.bottdev.meshdi.core;

import lombok.NonNull;
import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.kern.commons.registry.Registry;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.diagnostic.BeanDestroyFailed;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleException;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleBeanLifecycleManager implements BeanLifecycleManager {

    private static final System.Logger LOGGER = System.getLogger(SimpleBeanLifecycleManager.class.getName());

    /// Default sink for destroy failures: logs them, so a failure is never silently dropped.
    public static final DiagnosticSink<BeanDestroyFailed> LOGGING_DESTROY_SINK = diagnostic ->
            LOGGER.log(System.Logger.Level.ERROR, diagnostic.message(), diagnostic.cause());

    private final Registry<ScopeType, BeanScope> scopeRegistry;
    private final DiagnosticSink<BeanDestroyFailed> destroyFailureSink;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public SimpleBeanLifecycleManager(@NonNull Registry<ScopeType, BeanScope> scopeRegistry) {
        this(scopeRegistry, LOGGING_DESTROY_SINK);
    }

    /// @param destroyFailureSink receives one diagnostic per bean that failed to be destroyed in [#dispose()].
    public SimpleBeanLifecycleManager(
            @NonNull Registry<ScopeType, BeanScope> scopeRegistry,
            @NonNull DiagnosticSink<BeanDestroyFailed> destroyFailureSink
    ) {
        this.scopeRegistry = scopeRegistry;
        this.destroyFailureSink = destroyFailureSink;
    }

    private <T> Optional<BeanScope> getScopeByBinding(Binding<T> binding) {
        return scopeRegistry.find(binding.getScopeType());
    }

    private <T> BeanScope requireScope(Binding<T> binding) throws BeanLifecycleException {
        if (disposed.get()) throw new BeanLifecycleException(
                "Lifecycle manager is disposed, cannot create bean " + binding.getKey() + "."
        );
        return getScopeByBinding(binding).orElseThrow(() -> new BeanLifecycleException(
                "Failed to find bean scope " + binding.getScopeType() + " of " + binding.getKey() + "."
        ));
    }

    @Override
    public <T> boolean contains(Binding<T> binding) {
        return getScopeByBinding(binding)
                .map(scope -> scope.contains(binding.getKey()))
                .orElse(false);
    }

    @Override
    public <T> T get(Binding<T> binding) {
        return find(binding).orElse(null);
    }

    @Override
    public <T> Optional<T> find(Binding<T> binding) {
        return getScopeByBinding(binding)
                .map(scope -> scope.get(binding.getKey()))
                .map(BeanInstance::value);
    }

    private interface ScopeCall<T> {
        T apply(BeanScope scope) throws BeanCreationException, BeanLifecycleEventHandleException;
    }

    private <T> T callScope(Binding<T> binding, ScopeCall<T> call) throws BeanLifecycleException {
        BeanScope scope = requireScope(binding);
        try {
            return call.apply(scope);

        } catch (BeanResolvationException ex) {
            throw new BeanLifecycleException(
                    "Failed to resolve dependency for bean " + binding.getKey() + ".", ex
            );

        } catch (BeanCreationException ex) {
            throw new BeanLifecycleException(
                    "Failed to create bean " + binding.getKey() + ".", ex
            );

        }
    }

    @Override
    public <T> T create(Binding<T> binding, BeanResolver resolver) throws BeanLifecycleException {
        return callScope(binding, scope -> scope.create(binding, resolver));
    }

    @Override
    public <T> T getOrCreate(Binding<T> binding, BeanResolver resolver) throws BeanLifecycleException {
        return callScope(binding, scope -> scope.getOrCreate(binding, resolver));
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {

            for (BeanScope scope : scopeRegistry.getAll()) {

                for (BeanInstance<?> instance : scope.getDestroyOrder()) {
                    destroy(instance);
                }

                try {
                    scope.dispose();

                } catch (RuntimeException ex) {
                    LOGGER.log(System.Logger.Level.ERROR, "Failed to dispose bean scope " + scope + ".", ex);

                }

            }
            scopeRegistry.clear();

        }
    }

    /// Runs `PRE_DESTROY` first, so the handler still sees a usable bean, then `Disposable.dispose()`.
    /// Both steps always run and no failure escapes: one broken bean must not stop the others from being destroyed.
    private <T> void destroy(BeanInstance<T> instance) {
        T value = instance.value();
        try {
            instance.binding().invokeEventHandler(BeanLifecycleEventType.PRE_DESTROY, value);

        } catch (Exception ex) {
            reportDestroyFailure(instance, ex);

        }

        if (value instanceof Disposable disposable) {
            try {
                disposable.dispose();

            } catch (Exception ex) {
                reportDestroyFailure(instance, ex);

            }
        }

    }

    private void reportDestroyFailure(BeanInstance<?> instance, Exception failure) {
        try {
            destroyFailureSink.accept(new BeanDestroyFailed(instance.binding().getKey(), failure));

        } catch (RuntimeException sinkFailure) {
            failure.addSuppressed(sinkFailure);
            LOGGER.log(System.Logger.Level.ERROR, "Failed to report a bean destroy failure.", failure);

        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
