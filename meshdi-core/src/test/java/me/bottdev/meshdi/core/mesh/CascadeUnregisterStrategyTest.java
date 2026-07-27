package me.bottdev.meshdi.core.mesh;

import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.api.MeshUnregisterCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CascadeUnregisterStrategyTest {

    static CascadeUnregisterStrategy strategy;
    static ContextMesh mesh;

    @BeforeAll
    static void setUp() {
        strategy = new CascadeUnregisterStrategy();

        mesh = mock(ContextMesh.class);
        when(mesh.getDependingContexts("root")).thenReturn(Set.of("intermediate"));
        when(mesh.getDependingContexts("intermediate")).thenReturn(Set.of("ctxA", "ctxB"));
        when(mesh.getDependingContexts("ctxA")).thenReturn(Set.of("ctxA1", "ctxA2"));
    }

    @Test
    @DisplayName("collect: collects context ids in cascade post-order")
    void createCommand_cascadePostOrder() {

        MeshUnregisterCommand rootCommand = strategy.createCommand(mesh, "root", _ -> {});
        MeshUnregisterCommand intermediateCommand = strategy.createCommand(mesh, "intermediate", _ -> {});
        MeshUnregisterCommand ctxBCommand = strategy.createCommand(mesh, "ctxB", _ -> {});
        MeshUnregisterCommand ctxACommand = strategy.createCommand(mesh, "ctxA", _ -> {});
        MeshUnregisterCommand ctxA1Command = strategy.createCommand(mesh, "ctxA1", _ -> {});
        MeshUnregisterCommand ctxA2Command = strategy.createCommand(mesh, "ctxA2", _ -> {});
        System.out.println(rootCommand);
        System.out.println(intermediateCommand);
        System.out.println(ctxBCommand);
        System.out.println(ctxACommand);
        System.out.println(ctxA1Command);
        System.out.println(ctxA2Command);


    }

}