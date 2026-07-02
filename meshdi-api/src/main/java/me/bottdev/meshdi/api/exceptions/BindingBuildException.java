package me.bottdev.meshdi.api.exceptions;

public class BindingBuildException extends DiException {

    public BindingBuildException(String message) {
        super(message);
    }

    public BindingBuildException(String message, Throwable cause) {
      super(message, cause);
    }

}
