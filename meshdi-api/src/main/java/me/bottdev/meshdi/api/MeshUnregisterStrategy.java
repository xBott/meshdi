package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshUnregisterPlanException;

import java.util.function.Consumer;

public interface MeshUnregisterStrategy {

    MeshUnregisterCommand createCommand(
            ContextMesh mesh,
            String root,
            Consumer<String> unregisterHandler
    ) throws MeshUnregisterPlanException;

}
