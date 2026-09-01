package me.bottdev.meshdi.api.exceptions;

public class ContextBootstrapException extends DiException {

    public ContextBootstrapException(String message) {
        super(message);
    }

    public ContextBootstrapException(String message, Throwable cause) {
      super(message, cause);
    }


}
