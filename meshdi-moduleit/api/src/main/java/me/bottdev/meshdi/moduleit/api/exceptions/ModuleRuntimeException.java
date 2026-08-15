package me.bottdev.meshdi.moduleit.api.exceptions;

public class ModuleRuntimeException extends RuntimeException {

    public ModuleRuntimeException(String message) {
        super(message);
    }

    public ModuleRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
