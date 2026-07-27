package me.bottdev.meshdi.core;

import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.core.mesh.DAGContextMesh;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DAGContextMeshTest {

    DAGContextMesh mesh;

    @BeforeEach
    void setUp() {
        mesh = new DAGContextMesh();
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
        void contains_registered() throws MeshRegisterException {
            Context context = mock(Context.class);
            when(context.getId()).thenReturn("root");
            mesh.register(context).submit();

            assertTrue(mesh.contains("root"));
        }

    }

    @Nested
    class Registration {

        @Test
        @DisplayName("register: successful registration of an independent context")
        void register_independent() throws MeshRegisterException {

            boolean before = mesh.contains("root");

            Context context = mock(Context.class);
            when(context.getId()).thenReturn("root");
            mesh.register(context).submit();

            assertFalse(before);
            assertTrue(mesh.contains("root"));


        }

        @Test
        @DisplayName("register: double registration of same context throws")
        void register_alreadyExists() {

            Context context = mock(Context.class);
            when(context.getId()).thenReturn("root");

            assertThrows(MeshRegisterException.class, () -> mesh
                    .register(context)
                    .submit()
                    .register(context)
                    .submit()
            );

        }

        @Test
        @DisplayName("register: circular dependency throws")
        void register_circularDependency() {

            Context contextA = mock(Context.class);
            when(contextA.getId()).thenReturn("ctxA");

            Context contextB = mock(Context.class);
            when(contextB.getId()).thenReturn("ctxB");

            assertThrows(MeshRegisterException.class, () -> mesh
                    .register(contextA)
                    .sees("ctxB")
                    .submit()
                    .register(contextB)
                    .sees("ctxA")
                    .submit()
            );

        }

        @Test
        @DisplayName("register: missing context throws")
        void register_missingContext() {

            Context contextA = mock(Context.class);
            when(contextA.getId()).thenReturn("ctxA");

            assertThrows(MeshRegisterException.class, () -> mesh
                    .register(contextA)
                    .sees("root")
                    .submit()
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
        void get_registered() throws MeshRegisterException {
            Context context = mock(Context.class);
            when(context.getId()).thenReturn("root");
            mesh.register(context).submit();

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
        void find_registered() throws MeshRegisterException {
            Context context = mock(Context.class);
            when(context.getId()).thenReturn("root");
            mesh.register(context).submit();

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
        void get_registered() throws MeshRegisterException {
            Context root = mock(Context.class);
            when(root.getId()).thenReturn("root");

            Context intermediate = mock(Context.class);
            when(root.getId()).thenReturn("intermediate");

            Context ctxA = mock(Context.class);
            when(root.getId()).thenReturn("ctxA");

            Context ctxB = mock(Context.class);
            when(root.getId()).thenReturn("ctxB");

            mesh
                    .register(root)
                    .submit()

                    .register(intermediate)
                    .sees("root")
                    .submit()

                    .register(ctxA)
                    .sees("intermediate")
                    .submit()

                    .register(ctxB)
                    .sees("ctxA")
                    .sees("intermediate")
                    .submit();

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
                    .containsExactly("intermediate", "root");

            assertThat(reachableFromCtxB)
                    .hasSize(3)
                    .containsExactly("ctxA", "intermediate", "root");

        }

    }

}