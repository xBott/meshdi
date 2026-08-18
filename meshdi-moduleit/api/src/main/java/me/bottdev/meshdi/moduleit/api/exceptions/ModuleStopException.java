package me.bottdev.meshdi.moduleit.api.exceptions;

public class ModuleStopException extends ModuleException {

    public ModuleStopException(String message) {
        super(message);
    }

    public ModuleStopException(String message, Throwable cause) {
        super(message, cause);
    }

}
