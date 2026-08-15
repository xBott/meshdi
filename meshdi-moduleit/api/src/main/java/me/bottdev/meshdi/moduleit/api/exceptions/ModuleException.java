package me.bottdev.meshdi.moduleit.api.exceptions;

public class ModuleException extends Exception {

    public ModuleException(String message) {
        super(message);
    }

    public ModuleException(String message, Throwable cause) {
        super(message, cause);
    }

}
