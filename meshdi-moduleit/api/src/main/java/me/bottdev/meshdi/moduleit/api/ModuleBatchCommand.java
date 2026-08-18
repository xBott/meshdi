package me.bottdev.meshdi.moduleit.api;

import java.util.List;

/// An intermediate representation of module operation that affects several modules.
/// @param <R> result of the operation.
public interface ModuleBatchCommand<R> {

    /// @return A list of module handles that will be affected by the operation.
    List<ModuleHandle> handles();

    /// Executes the operation.
    R confirm();

}
