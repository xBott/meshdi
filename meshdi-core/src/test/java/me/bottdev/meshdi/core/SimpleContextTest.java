package me.bottdev.meshdi.core;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.ContextBuilder;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.api.diagnostic.BeanDestroyFailed;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.api.exceptions.DuplicateBindingException;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static me.bottdev.kern.commons.key.KeyUtils.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Behaviour of a context built by [SimpleContextBuilder]: resolution, ordering and destruction.
class SimpleContextTest {

    static class Repository {}

    static class Service {
        final Repository repository;
        Service(Repository repository) { this.repository = repository; }
    }

    static class Lenient {
        final Repository repository;
        Lenient(@Dependency(link = DependencyLink.OPTIONAL) Repository repository) { this.repository = repository; }
    }

    @Nested
    class Resolution {

        @Test
        @DisplayName("get: throws for a key without a binding instead of returning null")
        void get_throwsWhenMissing() throws Exception {
            Context context = new SimpleContextBuilder().id("ctx").build();

            assertThatThrownBy(() -> context.get(Repository.class))
                    .isInstanceOf(BeanResolvationException.class)
                    .hasMessageContaining(Repository.class.getName());
        }

        @Test
        @DisplayName("find: returns an empty Optional for a key without a binding")
        void find_returnsEmptyWhenMissing() throws Exception {
            Context context = new SimpleContextBuilder().id("ctx").build();

            assertThat(context.resolver().find(key(Repository.class))).isEmpty();
        }

        @Test
        @DisplayName("optional dependency: injected as null when it is not bound")
        void optionalDependency_isNullWhenMissing() throws Exception {
            Context context = new SimpleContextBuilder().id("ctx")
                    .construct(Lenient.class)
                    .build();

            assertThat(context.get(Lenient.class).repository).isNull();
        }

        @Test
        @DisplayName("optional dependency: injected when it is bound")
        void optionalDependency_isInjectedWhenPresent() throws Exception {
            Context context = new SimpleContextBuilder().id("ctx")
                    .construct(Lenient.class)
                    .construct(Repository.class)
                    .build();

            assertThat(context.get(Lenient.class).repository).isSameAs(context.get(Repository.class));
        }

    }

    @Nested
    class Registration {

        @Test
        @DisplayName("binding: a second binding for the same key is rejected")
        void binding_rejectsDuplicateKey() {
            ContextBuilder builder = new SimpleContextBuilder().id("ctx")
                    .constant(key(String.class), "first");

            assertThatThrownBy(() -> builder.constant(key(String.class), "second"))
                    .isInstanceOf(DuplicateBindingException.class);
        }

        @Test
        @DisplayName("override: replaces an existing binding on purpose")
        void override_replacesBinding() throws Exception {
            ContextBuilder builder = new SimpleContextBuilder().id("ctx")
                    .constant(key(String.class), "first");
            builder.override(me.bottdev.meshdi.core.bindings.FactoryBinding.builder(key(String.class))
                    .factory(_ -> "second"));

            assertThat(builder.build().get(String.class)).isEqualTo("second");
        }

        @Test
        @DisplayName("merge: a duplicate key is rejected and nothing is merged")
        void merge_rejectsDuplicateKey() {
            ContextBuilder target = new SimpleContextBuilder().id("ctx")
                    .constant(key(String.class), "first");
            ContextBuilder other = new SimpleContextBuilder()
                    .constant(key(Integer.class), 1)
                    .constant(key(String.class), "second");

            assertThatThrownBy(() -> target.merge(other)).isInstanceOf(DuplicateBindingException.class);
            assertThat(target.bindings()).containsOnlyKeys(key(String.class));
        }

        @Test
        @DisplayName("start: eager beans are initialised dependencies first, whatever the registration order")
        void start_initialisesEagerBeansInTopologicalOrder() throws Exception {
            List<String> order = new ArrayList<>();
            Context context = new SimpleContextBuilder().id("ctx")
                    // Registered dependents-first on purpose.
                    .factory(key(String.class, "c"), config -> config
                            .dependsOn(key(String.class, "b"))
                            .factory(resolver -> resolver.get(key(String.class, "b")) + "c")
                            .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, _ -> order.add("c"))
                            .eager())
                    .factory(key(String.class, "b"), config -> config
                            .dependsOn(key(String.class, "a"))
                            .factory(resolver -> resolver.get(key(String.class, "a")) + "b")
                            .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, _ -> order.add("b"))
                            .eager())
                    .factory(key(String.class, "a"), config -> config
                            .factory(_ -> "a")
                            .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, _ -> order.add("a"))
                            .eager())
                    .build();

            context.start();

            assertThat(order).containsExactly("a", "b", "c");
            assertThat(context.bindingManager().getBindings())
                    .extracting(binding -> binding.getKey().qualifier())
                    .containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("start: a failing eager bean is not left cached")
        void start_doesNotCacheFailedEagerBean() throws Exception {
            Context context = new SimpleContextBuilder().id("ctx")
                    .factory(key(Repository.class), config -> config
                            .factory(_ -> new Repository())
                            .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, _ -> {
                                throw new IllegalStateException("boom");
                            })
                            .eager())
                    .build();

            assertThatThrownBy(context::start).isInstanceOf(ContextStartException.class);
            assertThat(context.lifecycleManager().contains(context.bindingManager().getBinding(key(Repository.class))))
                    .isFalse();
        }

    }

    @Nested
    class Destruction {

        static class Resource implements Disposable {
            final List<String> events;
            final String name;
            boolean disposed;

            Resource(List<String> events, String name) {
                this.events = events;
                this.name = name;
            }

            @Override
            public void dispose() {
                disposed = true;
                events.add(name + ":dispose");
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }
        }

        @Test
        @DisplayName("dispose: PRE_DESTROY runs before Disposable.dispose, so the handler sees a live bean")
        void dispose_runsPreDestroyBeforeDispose() throws Exception {
            List<String> events = new ArrayList<>();
            Context context = new SimpleContextBuilder().id("ctx")
                    .factory(key(Resource.class), config -> config
                            .factory(_ -> new Resource(events, "r"))
                            .eventHandler(BeanLifecycleEventType.PRE_DESTROY,
                                    bean -> events.add("r:preDestroy disposed=" + bean.isDisposed())))
                    .build();
            context.get(Resource.class);

            context.dispose();

            assertThat(events).containsExactly("r:preDestroy disposed=false", "r:dispose");
        }

        @Test
        @DisplayName("dispose: one failing bean does not stop the others and is reported to the sink")
        void dispose_continuesAfterFailure() throws Exception {
            List<String> events = new ArrayList<>();
            List<BeanDestroyFailed> failures = new ArrayList<>();
            Context context = new SimpleContextBuilder().id("ctx")
                    .destroyFailureSink(failures::add)
                    .factory(key(Resource.class, "first"), config -> config
                            .factory(_ -> new Resource(events, "first")))
                    .factory(key(Resource.class, "broken"), config -> config
                            .factory(_ -> new Resource(events, "broken") {
                                @Override
                                public void dispose() {
                                    throw new IllegalStateException("cannot close");
                                }
                            }))
                    .factory(key(Resource.class, "last"), config -> config
                            .factory(_ -> new Resource(events, "last")))
                    .build();
            context.get(Resource.class, "first");
            context.get(Resource.class, "broken");
            context.get(Resource.class, "last");

            context.dispose();

            assertThat(events).containsExactly("last:dispose", "first:dispose");
            assertThat(failures).singleElement().satisfies(failure -> {
                assertThat(failure.key()).isEqualTo(key(Resource.class, "broken"));
                assertThat(failure.cause()).hasMessage("cannot close");
            });
        }

    }

}
