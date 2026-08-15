package me.bottdev.meshdi.core.mesh;

import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.api.MeshUnregisterCommand;
import me.bottdev.meshdi.api.MeshUnregisterStrategy;

import java.util.*;
import java.util.function.Consumer;

/// Implementation of [MeshUnregisterStrategy] unregister strategy that
/// uses **post-order DFS** to find a cascade order of all contexts depending on
/// the one that needs to be unregistered.
public class CascadeUnregisterStrategy implements MeshUnregisterStrategy {

    @Override
    public MeshUnregisterCommand createCommand(
            ContextMesh<?> mesh,
            String root,
            Consumer<String> unregisterHandler
    ) {

        Set<String> visited = new HashSet<>();
        List<String> ordered = new ArrayList<>();

        collectRecursive(mesh, root, visited, ordered);

        return new MeshUnregisterCommand(ordered, unregisterHandler);

    }

    private void collectRecursive(
            ContextMesh<?> mesh,
            String current,
            Set<String> visited,
            List<String> order
    ) {
        if (!visited.add(current)) {
            return;
        }

        for (String dependent : mesh.getDependingContexts(current)) {
            collectRecursive(mesh, dependent, visited, order);
        }

        order.add(current);
    }

}
