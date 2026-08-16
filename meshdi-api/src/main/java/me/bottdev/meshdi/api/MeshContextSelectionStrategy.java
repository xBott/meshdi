package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;

import java.util.List;

/// Strategy that selects a group of contexts in a mesh to perform an operation.
public interface MeshContextSelectionStrategy {

    List<String> select(String id, ContextMesh<?> mesh) throws MeshContextSelectionException;

}
