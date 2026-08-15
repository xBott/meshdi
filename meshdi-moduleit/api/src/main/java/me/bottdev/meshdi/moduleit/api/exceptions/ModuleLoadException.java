package me.bottdev.meshdi.moduleit.api.exceptions;

public class ModuleLoadException extends ModuleException {

    public ModuleLoadException(String message) {
        super(message);
    }

    public ModuleLoadException(String message, Throwable cause) {
        super(message, cause);
    }

}
