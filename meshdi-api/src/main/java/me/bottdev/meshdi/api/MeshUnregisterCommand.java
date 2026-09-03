package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterExecuteException;

import java.util.List;
import java.util.function.Consumer;

/// An executable command that performs the unregistration of a group of contexts from a mesh.
///
/// This command ensures that contexts are unregistered in the correct, ordered sequence
/// (typically leaf nodes first) to prevent dangling dependencies.
///
/// @param ordered the ordered list of context IDs to unregister
/// @param unregisterHandler the handler that performs the actual unregistration logic
public record MeshUnregisterCommand(
        List<String> ordered,
        Consumer<String> unregisterHandler
) {

    /// Executes the unregistration process for all selected contexts in the planned order.
    ///
    /// @throws MeshUnregisterExecuteException if any context fails to unregister
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
