package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshRegistrationBuildException;

public interface MeshRegistration {

    interface Builder {

        MeshRegistration build() throws MeshRegistrationBuildException;

    }

    Context context();

}
