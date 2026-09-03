package me.bottdev.meshdi.api;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegistrationBuildException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Represents the registration metadata for adding a [Context] to a [ContextMesh].
///
/// This record contains the context to be registered and a list of context IDs it explicitly sees
/// (i.e., depends on and can look up beans from).
///
/// @param context the context being registered
/// @param sees the list of context IDs this context can see
public record MeshRegistration(
        @NonNull Context context,
        @NonNull List<String> sees
) {

    /// A builder for creating [MeshRegistration] instances.
    @RequiredArgsConstructor
    public static class Builder {

        private final Context context;
        private final List<String> sees = new ArrayList<>();

        /// Adds a context ID to the list of contexts this one can see.
        ///
        /// @param id the target context ID
        /// @return this builder instance
        public Builder sees(String id) {
            sees.add(id);
            return this;
        }

        /// Builds the final [MeshRegistration].
        ///
        /// @return the registration metadata
        /// @throws MeshRegistrationBuildException if the configuration is invalid
        public MeshRegistration build()
                throws MeshRegistrationBuildException
        {
            try {
                Objects.requireNonNull(context, "Context must be non-null.");
                return new MeshRegistration(context, List.copyOf(sees));

            } catch (Exception ex) {
                throw new MeshRegistrationBuildException("Failed to build a DAG Mesh registration.", ex);
            }
        }

    }

    /// Creates a new builder for the specified context.
    ///
    /// @param context the context to register
    /// @return a new builder
    public static Builder builder(Context context) {
        return new Builder(context);
    }

    public MeshRegistration(
            @NonNull Context context,
            @NonNull List<String> sees
    ) {
        this.context = context;
        this.sees = List.copyOf(sees);
    }

}
