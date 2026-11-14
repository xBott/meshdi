package me.bottdev.meshdi.core.scopes;

import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.exceptions.BeanCreationException;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;
import me.bottdev.meshdi.core.bindings.FactoryBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static me.bottdev.kern.commons.key.KeyUtils.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SingletonScopeTest {

    static class Service {}

    final BeanResolver resolver = mock(BeanResolver.class);
    final SingletonScope scope = new SingletonScope();

    @Test
    @DisplayName("getOrCreate: concurrent first requests create the bean once and all get the same instance")
    void getOrCreate_isAtomicUnderConcurrency() throws Exception {
        // Given
        int threads = 16;
        AtomicInteger created = new AtomicInteger();
        FactoryBinding<Service> binding = FactoryBinding.builder(key(Service.class))
                .factory(_ -> {
                    created.incrementAndGet();
                    java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
                    return new Service();
                })
                .build();

        CountDownLatch start = new CountDownLatch(1);
        Set<Service> seen = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    seen.add(scope.getOrCreate(binding, resolver));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        // Then
        assertThat(created).hasValue(1);
        assertThat(seen).hasSize(1);
    }

    @Test
    @DisplayName("getOrCreate: the instance is not visible before POST_CONSTRUCT has finished")
    void getOrCreate_publishesAfterPostConstruct() throws Exception {
        // Given
        AtomicBoolean visibleDuringHandler = new AtomicBoolean(true);
        FactoryBinding<Service> binding = FactoryBinding.builder(key(Service.class))
                .factory(_ -> new Service())
                .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT,
                        _ -> visibleDuringHandler.set(scope.contains(key(Service.class))))
                .build();

        // When
        scope.getOrCreate(binding, resolver);

        // Then
        assertThat(visibleDuringHandler).isFalse();
        assertThat(scope.contains(key(Service.class))).isTrue();
    }

    @Test
    @DisplayName("getOrCreate: a failed POST_CONSTRUCT caches nothing and the next request retries")
    void getOrCreate_doesNotCacheHalfInitialisedBean() throws Exception {
        // Given
        AtomicInteger attempts = new AtomicInteger();
        FactoryBinding<Service> binding = FactoryBinding.builder(key(Service.class))
                .factory(_ -> new Service())
                .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, _ -> {
                    if (attempts.incrementAndGet() == 1) throw new IllegalStateException("boom");
                })
                .build();

        // When & Then
        assertThatThrownBy(() -> scope.getOrCreate(binding, resolver))
                .isInstanceOf(BeanLifecycleEventHandleException.class);
        assertThat(scope.contains(key(Service.class))).isFalse();
        assertThat(scope.getDestroyOrder()).isEmpty();

        Service second = scope.getOrCreate(binding, resolver);
        assertThat(second).isNotNull();
        assertThat(scope.getOrCreate(binding, resolver)).isSameAs(second);
    }

    @Test
    @DisplayName("create: throws when the singleton already exists")
    void create_failsWhenAlreadyCreated() throws Exception {
        FactoryBinding<Service> binding = FactoryBinding.builder(key(Service.class))
                .factory(_ -> new Service())
                .build();
        scope.create(binding, resolver);

        assertThatThrownBy(() -> scope.create(binding, resolver))
                .isInstanceOf(BeanCreationException.class);
    }

    @Test
    @DisplayName("getDestroyOrder: beans are returned in reverse creation order")
    void getDestroyOrder_isReverseCreationOrder() throws Exception {
        FactoryBinding<Service> first = FactoryBinding.builder(key(Service.class, "first"))
                .factory(_ -> new Service())
                .build();
        FactoryBinding<Service> second = FactoryBinding.builder(key(Service.class, "second"))
                .factory(_ -> new Service())
                .build();
        scope.getOrCreate(first, resolver);
        scope.getOrCreate(second, resolver);

        assertThat(scope.getDestroyOrder())
                .extracting(instance -> instance.binding().getKey().qualifier())
                .containsExactly("second", "first");
    }

    @Test
    @DisplayName("getOrCreate: fails after the scope is disposed")
    void getOrCreate_failsWhenDisposed() throws Exception {
        FactoryBinding<Service> binding = FactoryBinding.builder(key(Service.class))
                .factory(_ -> new Service())
                .build();
        scope.dispose();

        assertThatThrownBy(() -> scope.getOrCreate(binding, resolver))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("disposed");
    }

}
