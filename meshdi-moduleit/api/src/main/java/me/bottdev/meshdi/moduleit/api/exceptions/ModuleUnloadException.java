package me.bottdev.meshdi.moduleit.api.exceptions;

public class ModuleUnloadException extends ModuleException {

    public ModuleUnloadException(String message) {
        super(message);
    }

    public ModuleUnloadException(String message, Throwable cause) {
        super(message, cause);
    }

}
