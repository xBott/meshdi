package me.bottdev.meshdi.api.exceptions.mesh;

import me.bottdev.meshdi.api.exceptions.DiException;

public class ContextMeshException extends DiException {

    public ContextMeshException(String message) {
        super(message);
    }

    public ContextMeshException(String message, Throwable cause) {
        super(message, cause);
    }

}
