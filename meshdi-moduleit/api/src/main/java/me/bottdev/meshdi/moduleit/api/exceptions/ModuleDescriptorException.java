package me.bottdev.meshdi.moduleit.api.exceptions;

public class ModuleDescriptorException extends ModuleRuntimeException {

    public ModuleDescriptorException(String message) {
        super(message);
    }

    public ModuleDescriptorException(String message, Throwable cause) {
        super(message, cause);
    }

}
