package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;

import java.util.List;

/// A strategy used to select a group of dependent contexts within a [ContextMesh] for a bulk operation.
///
/// This is typically used when unregistering a context to determine which other contexts
/// should also be unregistered (e.g., cascading unregistration).
public interface MeshContextSelectionStrategy {

    /// Selects the contexts to include in the operation based on the target context ID.
    ///
    /// @param id the ID of the target context
    /// @param mesh the mesh containing the contexts
    /// @return a list of context IDs selected by the strategy
    /// @throws MeshContextSelectionException if an error occurs during selection
    List<String> select(String id, ContextMesh mesh) throws MeshContextSelectionException;

}
