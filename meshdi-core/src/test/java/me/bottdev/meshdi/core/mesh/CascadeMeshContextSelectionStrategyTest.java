package me.bottdev.meshdi.core.mesh;

import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.core.mesh.strategy.CascadeMeshContextSelectionStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CascadeMeshContextSelectionStrategyTest {

    static CascadeMeshContextSelectionStrategy strategy;

    @BeforeAll
    static void setUp() {
        strategy = new CascadeMeshContextSelectionStrategy();
    }

    @Test
    @DisplayName("select: selects cascade affected contexts in correct unload order")
    void select_cascade() {

        Context contextA = mock(Context.class);
        when(contextA.id()).thenReturn("a");

        Context contextB = mock(Context.class);
        when(contextB.id()).thenReturn("b");

        Context contextC = mock(Context.class);
        when(contextC.id()).thenReturn("c");

        Context contextD = mock(Context.class);
        when(contextD.id()).thenReturn("d");

        Context contextE = mock(Context.class);
        when(contextE.id()).thenReturn("e");

        Context contextF = mock(Context.class);
        when(contextF.id()).thenReturn("f");

        ContextMesh mesh = mock(ContextMesh.class);
        when(mesh.contains(anyString())).thenReturn(true);
        when(mesh.getContexts()).thenReturn(List.of(contextA, contextB, contextC, contextD, contextE, contextF));
        when(mesh.getDependingContexts("a")).thenReturn(Set.of("b"));
        when(mesh.getDependingContexts("b")).thenReturn(Set.of("c", "d"));
        when(mesh.getDependingContexts("c")).thenReturn(Set.of("e", "f"));
        when(mesh.getDependingContexts("d")).thenReturn(Set.of("f"));
        when(mesh.getDependingContexts("e")).thenReturn(Set.of());
        when(mesh.getDependingContexts("f")).thenReturn(Set.of());

        List<String> groupA = strategy.select("a", mesh);
        List<String> groupB = strategy.select("b", mesh);
        List<String> groupC = strategy.select("c", mesh);
        List<String> groupD = strategy.select("d", mesh);
        List<String> groupE = strategy.select("e", mesh);
        List<String> groupF = strategy.select("f", mesh);

        assertThat(groupA)
                .hasSize(6)
                .containsExactly("f", "e", "d", "c", "b", "a");

        assertThat(groupB)
                .hasSize(5)
                .containsExactly("f", "e", "d", "c", "b");

        assertThat(groupC)
                .hasSize(3)
                .containsExactly("f", "e", "c");

        assertThat(groupD)
                .hasSize(2)
                .containsExactly("f", "d");

        assertThat(groupE)
                .hasSize(1)
                .containsExactly("e");

        assertThat(groupF)
                .hasSize(1)
                .containsExactly("f");


    }

    @Test
    @DisplayName("select: throws when context does not exist")
    void select_nonExisting() {

        ContextMesh mesh = mock(ContextMesh.class);
        when(mesh.contains(anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> strategy.select("a", mesh));

    }

}