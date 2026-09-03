package me.bottdev.meshdi.api;

import me.bottdev.kern.commons.Disposable;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshContextSelectionException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/// A special structure that connects several [Context] instances into one ecosystem.
///
/// The Mesh allows multiple isolated contexts to communicate, share beans, and resolve
/// dependencies across module boundaries.
public interface ContextMesh extends Disposable {

    /// Checks if a context with the given ID is registered in this mesh.
    ///
    /// @param id the context ID
    /// @return `true` if the context is registered, `false` otherwise
    boolean contains(String id);

    /// Registers a new context within this mesh according to the provided registration metadata.
    ///
    /// @param registration the registration details, including visibility rules
    /// @return the registered context
    /// @throws MeshRegisterException if registration fails (e.g., ID collision or invalid dependencies)
    Context register(MeshRegistration registration) throws MeshRegisterException;

    /// Plans the unregistration of a context and returns an executable command.
    ///
    /// @param id the ID of the context to unregister
    /// @param strategy the strategy used to select which dependent contexts to unregister alongside it
    /// @return an executable command to perform the unregistration
    /// @throws MeshContextSelectionException if the strategy fails to select the contexts
    MeshUnregisterCommand planUnregister(String id, MeshContextSelectionStrategy strategy)
            throws MeshContextSelectionException;

    /// Retrieves the registration metadata for the specified context ID.
    ///
    /// @param id the context ID
    /// @return the mesh registration, or `null` if not found
    MeshRegistration get(String id);

    /// Retrieves all registered contexts in their sorted initialization order.
    ///
    /// @return a list of registered contexts
    List<Context> getContexts();

    /// Attempts to find the registration metadata for the specified context ID.
    ///
    /// @param id the context ID
    /// @return an [Optional] containing the registration if found, empty otherwise
    Optional<MeshRegistration> find(String id);

    /// Retrieves the IDs of all contexts that the specified context is explicitly allowed to see.
    ///
    /// @param id the ID of the querying context
    /// @return a set of visible context IDs
    Set<String> getVisibleContexts(String id);

    /// Retrieves the IDs of all contexts that depend on the specified context.
    ///
    /// @param id the target context ID
    /// @return a set of depending context IDs
    Set<String> getDependingContexts(String id);

    /// Retrieves all context IDs that can be reached from the specified context using Breadth-First Search.
    ///
    /// @param fromId the starting context ID
    /// @return a list of reachable context IDs
    List<String> getTransitiveContexts(String fromId);

    /// Checks if a bean with the specified key can be looked up from the given context.
    ///
    /// @param fromId the ID of the context performing the lookup
    /// @param key the typed key to look for
    /// @param <T> the type of the bean
    /// @return `true` if the bean is visible and can be resolved, `false` otherwise
    <T> boolean canLookup(String fromId, TypedKey<T> key);

    /// Attempts to look up a bean binding across the mesh from the perspective of the given context.
    ///
    /// @param fromId the ID of the context performing the lookup
    /// @param key the typed key to look for
    /// @param <T> the type of the bean
    /// @return an [Optional] containing the lookup result if successful, empty otherwise
    <T> Optional<ContextMeshLookup<T>> lookup(String fromId, TypedKey<T> key);

}
