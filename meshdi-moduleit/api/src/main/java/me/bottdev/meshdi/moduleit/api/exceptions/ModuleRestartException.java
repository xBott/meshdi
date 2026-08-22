package me.bottdev.meshdi.moduleit.api.exceptions;

public class ModuleRestartException extends ModuleException {

    public ModuleRestartException(String message) {
        super(message);
    }

    public ModuleRestartException(String message, Throwable cause) {
        super(message, cause);
    }

}
