package me.bottdev.meshdi.api.exceptions;

public class ContextBuildException extends DiException {
    public ContextBuildException(String message) {
        super(message);
    }

    public ContextBuildException(String message, Throwable cause) {
      super(message, cause);
    }

}
