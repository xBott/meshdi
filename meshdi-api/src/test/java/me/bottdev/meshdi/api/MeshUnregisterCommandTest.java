package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterExecuteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeshUnregisterCommandTest {

    @Test
    @DisplayName("execute: unregisters contexts in order")
    void execute_inOrder() throws MeshUnregisterExecuteException {
        // Given
        List<String> orderedContexts = List.of("ctxA", "ctxB", "ctxC");
        List<String> unregistered = new ArrayList<>();
        
        Consumer<String> unregisterHandler = unregistered::add;
        MeshUnregisterCommand command = new MeshUnregisterCommand(orderedContexts, unregisterHandler);

        // When
        command.execute();

        // Then
        assertThat(unregistered)
                .hasSize(3)
                .containsExactlyElementsOf(orderedContexts);
    }

    @Test
    @DisplayName("execute: throws and stops on handler failure")
    void execute_throwsOnFailure() {
        // Given
        List<String> orderedContexts = List.of("ctxA", "ctxB", "ctxC");
        List<String> unregistered = new ArrayList<>();
        
        Consumer<String> failingHandler = contextId -> {
            if ("ctxB".equals(contextId)) {
                throw new RuntimeException("Simulated failure");
            }
            unregistered.add(contextId);
        };
        
        MeshUnregisterCommand command = new MeshUnregisterCommand(orderedContexts, failingHandler);

        // When & Then
        assertThatThrownBy(command::execute)
                .isInstanceOf(MeshUnregisterExecuteException.class);
                
        // Assert that execution stopped at the failing element
        assertThat(unregistered).containsExactly("ctxA");
    }
}