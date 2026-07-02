package me.bottdev.meshdi.core;

import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.exceptions.ContextMeshRegistrationException;
import org.junit.jupiter.api.*;

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
        }

        @Test
        @DisplayName("contains: returns true if registered")
        void contains_registered() {
        }

    }

    @Nested
    class Registration {

        @Test
        @DisplayName("register: successful registration of an independent context")
        void register_independent() throws ContextMeshRegistrationException {

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

            assertThrows(ContextMeshRegistrationException.class, () -> mesh
                    .register(context)
                    .submit()
                    .register(context)
                    .submit()
            );

        }

    }

}