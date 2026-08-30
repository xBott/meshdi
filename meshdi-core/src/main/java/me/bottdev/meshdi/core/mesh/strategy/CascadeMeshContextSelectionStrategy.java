package me.bottdev.meshdi.core.mesh.strategy;

import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.api.MeshContextSelectionStrategy;

import java.util.*;

/// Implementation of [MeshContextSelectionStrategy] that
/// uses **DFS** to find cascade affected contexts in the mesh and returns
/// a reversed sub-list of topologically sorted contexts.
public class CascadeMeshContextSelectionStrategy implements MeshContextSelectionStrategy {

    @Override
    public List<String> select(String id, ContextMesh mesh) {

        if (!mesh.contains(id))
            throw new IllegalArgumentException("Context \"" + id + "\" does not exist in a mesh.");

        Set<String> affected = new HashSet<>();
        collectAffectedModules(id, mesh, affected);

        return mesh.getContexts().reversed().stream()
                .map(Context::id)
                .filter(affected::contains)
                .toList();

    }

    private void collectAffectedModules(
            String current,
            ContextMesh mesh,
            Set<String> affected
    ) {
        if (!affected.add(current)) return;

        for (String dependent : mesh.getDependingContexts(current)) {
            collectAffectedModules(dependent, mesh, affected);
        }

    }

}
