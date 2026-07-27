package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterExecuteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeshUnregisterCommandTest {

    @Test
    @DisplayName("execute: should use consumer for all ordered contexts")
    void execute_usesConsumer() throws MeshUnregisterExecuteException {


        List<String> ordered = List.of("ctxA", "ctxB", "intermediate");
        List<String> unregistered = new ArrayList<>();

        MeshUnregisterCommand command = new MeshUnregisterCommand(ordered, unregistered::add);

        command.execute();

        assertThat(unregistered)
                .hasSize(3)
                .containsExactly("ctxA", "ctxB", "intermediate");

    }

    @Test
    @DisplayName("execute: throws domain exception when consumer throws")
    void execute_throws() {

        List<String> ordered = List.of("ctxA", "ctxB", "intermediate");

        MeshUnregisterCommand command = new MeshUnregisterCommand(ordered, contextId -> {
            if (contextId.length() > 4) throw new IllegalArgumentException("Context id is incorrect");
        });

        assertThrows(MeshUnregisterExecuteException.class, command::execute);

    }

}