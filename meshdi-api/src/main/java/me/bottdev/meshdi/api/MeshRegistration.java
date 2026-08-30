package me.bottdev.meshdi.api;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegistrationBuildException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record MeshRegistration(
        @NonNull Context context,
        @NonNull List<String> sees
) {

    @RequiredArgsConstructor
    public static class Builder {

        private final Context context;
        private final List<String> sees = new ArrayList<>();

        public Builder sees(String id) {
            sees.add(id);
            return this;
        }

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
