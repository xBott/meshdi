package me.bottdev.meshdi.api.exceptions;

public class ContextStartException extends DiException {
    public ContextStartException(String message) {
        super(message);
    }

    public ContextStartException(String message, Throwable cause) {
      super(message, cause);
    }

}
