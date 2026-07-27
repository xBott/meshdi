package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterExecuteException;

import java.util.List;
import java.util.function.Consumer;

public record MeshUnregisterCommand(
        List<String> ordered,
        Consumer<String> unregisterHandler
) {

    public void execute() throws MeshUnregisterExecuteException {
        for (String contextId : ordered) {
            try {
                unregisterHandler.accept(contextId);

            } catch (Exception ex) {
                throw new MeshUnregisterExecuteException(contextId);

            }
        }
    }

}
