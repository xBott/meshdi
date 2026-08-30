package me.bottdev.meshdi.core;

import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.MeshRegistration;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegistrationBuildException;
import me.bottdev.meshdi.core.mesh.DagContextMesh;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DagContextMeshTest {

    DagContextMesh mesh;

    @BeforeEach
    void setUp() {
        mesh = new DagContextMesh();
    }

    @AfterEach
    void tearDown() {
        mesh.dispose();
    }

    @Nested
    class Contains {

        @Test
        @DisplayName("contains: returns false if not registered")
        void contains_notRegistered() {
            assertFalse(mesh.contains("root"));
        }

        @Test
        @DisplayName("contains: returns true if registered")
        void contains_registered() throws MeshRegisterException, MeshRegistrationBuildException {
            Context context = mock(Context.class);
            when(context.id()).thenReturn("root");
            mesh.register(MeshRegistration.builder(context).build());

            assertTrue(mesh.contains("root"));
        }

    }

    @Nested
    class DagMeshRegistration {

        @Test
        @DisplayName("register: successful registration of an independent context")
        void register_independent() throws MeshRegisterException, MeshRegistrationBuildException {

            boolean before = mesh.contains("root");

            Context context = mock(Context.class);
            when(context.id()).thenReturn("root");
            mesh.register(MeshRegistration.builder(context).build());

            assertFalse(before);
            assertTrue(mesh.contains("root"));


        }

        @Test
        @DisplayName("register: double registration of same context throws")
        void register_alreadyExists() {

            Context context = mock(Context.class);
            when(context.id()).thenReturn("root");

            assertThrows(MeshRegisterException.class, () -> {
                mesh.register(MeshRegistration.builder(context).build());
                mesh.register(MeshRegistration.builder(context).build());
            });

        }

        @Test
        @DisplayName("register: circular dependency throws")
        void register_circularDependency() {

            Context contextA = mock(Context.class);
            when(contextA.id()).thenReturn("ctxA");

            Context contextB = mock(Context.class);
            when(contextB.id()).thenReturn("ctxB");

            assertThrows(MeshRegisterException.class, () -> {
                mesh.register(MeshRegistration.builder(contextA).sees("ctxB").build());
                mesh.register(MeshRegistration.builder(contextB).sees("ctxA").build());
            });

        }

        @Test
        @DisplayName("register: missing context throws")
        void register_missingContext() {

            Context contextA = mock(Context.class);
            when(contextA.id()).thenReturn("ctxA");

            assertThrows(MeshRegisterException.class, () -> mesh
                    .register(MeshRegistration.builder(contextA).sees("root").build())
            );

        }

    }

    @Nested
    class Get {

        @Test
        @DisplayName("get: returns null if not registered")
        void get_notRegistered() {
            assertNull(mesh.get("root"));
        }

        @Test
        @DisplayName("contains: returns context if registered")
        void get_registered() throws MeshRegisterException, MeshRegistrationBuildException {
            Context context = mock(Context.class);
            when(context.id()).thenReturn("root");
            mesh.register(MeshRegistration.builder(context).build());

            assertNotNull(mesh.get("root"));
        }

    }

    @Nested
    class Find {

        @Test
        @DisplayName("get: returns empty optional if not registered")
        void find_notRegistered() {
            assertFalse(mesh.find("root").isPresent());
        }

        @Test
        @DisplayName("contains: returns an optional with context if registered")
        void find_registered() throws MeshRegisterException, MeshRegistrationBuildException {
            Context context = mock(Context.class);
            when(context.id()).thenReturn("root");
            mesh.register(MeshRegistration.builder(context).build());

            assertTrue(mesh.find("root").isPresent());
        }

    }

    @Nested
    class GetReachable {

        @Test
        @DisplayName("getReachable: throws if \"from\" context is not registered")
        void get_notRegistered() {
            assertThrows(IllegalArgumentException.class, () ->
                    mesh.getTransitiveContexts("root")
            );
        }

        @Test
        @DisplayName("contains: returns all reachable context ids from a specified context in transitive way")
        void get_registered() throws MeshRegisterException, MeshRegistrationBuildException {
            Context root = mock(Context.class);
            when(root.id()).thenReturn("root");

            Context intermediate = mock(Context.class);
            when(intermediate.id()).thenReturn("intermediate");

            Context ctxA = mock(Context.class);
            when(ctxA.id()).thenReturn("ctxA");

            Context ctxB = mock(Context.class);
            when(ctxB.id()).thenReturn("ctxB");

            mesh.register(MeshRegistration.builder(root).build());
            mesh.register(MeshRegistration.builder(intermediate).sees("root").build());
            mesh.register(MeshRegistration.builder(ctxA).sees("intermediate").build());
            mesh.register(MeshRegistration.builder(ctxB).sees("intermediate").sees("ctxA").build());

            List<String> reachableFromRoot = mesh.getTransitiveContexts("root");
            List<String> reachableFromIntermediate = mesh.getTransitiveContexts("intermediate");
            List<String> reachableFromCtxA = mesh.getTransitiveContexts("ctxA");
            List<String> reachableFromCtxB = mesh.getTransitiveContexts("ctxB");

            assertThat(reachableFromRoot)
                    .isEmpty();

            assertThat(reachableFromIntermediate)
                    .hasSize(1)
                    .containsExactly("root");

            assertThat(reachableFromCtxA)
                    .hasSize(2)
                    .containsExactlyInAnyOrder("intermediate", "root");

            assertThat(reachableFromCtxB)
                    .hasSize(3)
                    .containsExactlyInAnyOrder("ctxA", "intermediate", "root");

        }

    }

}