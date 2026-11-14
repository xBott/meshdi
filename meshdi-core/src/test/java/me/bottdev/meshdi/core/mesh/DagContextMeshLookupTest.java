package me.bottdev.meshdi.core.mesh;

import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.MeshRegistration;
import me.bottdev.meshdi.api.exceptions.BeanResolvationException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshAmbiguousLookupException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static me.bottdev.kern.commons.key.KeyUtils.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Lookups through a real mesh of real contexts.
class DagContextMeshLookupTest {

    final DagContextMesh mesh = new DagContextMesh();

    @AfterEach
    void tearDown() {
        mesh.dispose();
    }

    private Context register(String id, String value, String... sees) throws Exception {
        SimpleContextBuilder builder = new SimpleContextBuilder().id(id);
        if (value != null) builder.constant(key(String.class), value);
        return mesh.register(new MeshRegistration(builder.build(), List.of(sees)));
    }

    @Test
    @DisplayName("lookup: a bean of a visible context is resolved through the mesh")
    void lookup_resolvesFromVisibleContext() throws Exception {
        register("root", "from-root");
        Context child = register("child", null, "root");

        assertThat(child.get(String.class)).isEqualTo("from-root");
        assertThat(mesh.canLookup("child", key(String.class))).isTrue();
    }

    @Test
    @DisplayName("lookup: a context that is not visible is not consulted")
    void lookup_ignoresInvisibleContexts() throws Exception {
        register("root", "from-root");
        Context stranger = register("stranger", null);

        assertThat(mesh.canLookup("stranger", key(String.class))).isFalse();
        assertThat(stranger.resolver().find(key(String.class))).isEmpty();
        assertThatThrownBy(() -> stranger.get(String.class)).isInstanceOf(BeanResolvationException.class);
    }

    @Test
    @DisplayName("lookup: a diamond reaches the shared context once and is not ambiguous")
    void lookup_diamondIsNotAmbiguous() throws Exception {
        register("root", "from-root");
        register("left", null, "root");
        register("right", null, "root");
        Context top = register("top", null, "left", "right");

        assertThat(top.get(String.class)).isEqualTo("from-root");
    }

    @Test
    @DisplayName("lookup: a key bound by two visible contexts is reported instead of picking one")
    void lookup_reportsAmbiguity() throws Exception {
        register("left", "from-left");
        register("right", "from-right");
        Context top = register("top", null, "left", "right");

        assertThatThrownBy(() -> top.get(String.class))
                .isInstanceOf(MeshAmbiguousLookupException.class)
                .satisfies(ex -> assertThat(((MeshAmbiguousLookupException) ex).getOwners())
                        .containsExactlyInAnyOrder("left", "right"));
    }

    @Test
    @DisplayName("lookup: a local binding wins over visible contexts")
    void lookup_localBindingWins() throws Exception {
        register("root", "from-root");
        Context child = register("child", "from-child", "root");

        assertThat(child.get(String.class)).isEqualTo("from-child");
    }

    @Test
    @DisplayName("lookup: the index follows the mesh when a context is unregistered")
    void lookup_seesUnregistration() throws Exception {
        register("root", "from-root");
        Context child = register("child", null, "root");
        assertThat(mesh.canLookup("child", key(String.class))).isTrue();

        mesh.planUnregister("root", (id, _) -> List.of(id)).execute();

        assertThat(mesh.canLookup("child", key(String.class))).isFalse();
        assertThat(child.resolver().find(key(String.class))).isEmpty();
    }

    @Test
    @DisplayName("register: a failed registration leaves no trace")
    void register_rejectsUnknownTarget() {
        assertThatThrownBy(() -> register("child", null, "missing"))
                .isInstanceOf(MeshRegisterException.class);
        assertThat(mesh.contains("child")).isFalse();
    }

    @Test
    @DisplayName("lookups stay consistent while contexts are registered and unregistered concurrently")
    void lookup_isSafeDuringConcurrentChanges() throws Exception {
        register("root", "from-root");
        Context reader = register("reader", null, "root");

        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            List<Future<Integer>> readers = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                readers.add(executor.submit(() -> {
                    started.countDown();
                    int lookups = 0;
                    while (running.get()) {
                        assertThat(reader.get(String.class)).isEqualTo("from-root");
                        assertThat(mesh.getTransitiveContexts("reader")).containsExactly("root");
                        lookups++;
                    }
                    return lookups;
                }));
            }

            started.await();
            for (int i = 0; i < 300; i++) {
                String id = "churn-" + i;
                register(id, null, "root");
                mesh.planUnregister(id, (target, _) -> List.of(target)).execute();
            }
            running.set(false);

            for (Future<Integer> future : readers) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isPositive();
            }
        }
    }

}
