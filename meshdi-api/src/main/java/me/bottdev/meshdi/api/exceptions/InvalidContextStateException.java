package me.bottdev.meshdi.api.exceptions;

public class InvalidContextStateException extends DiException {
    public InvalidContextStateException(String message) {
        super(message);
    }

    public InvalidContextStateException(String message, Throwable cause) {
      super(message, cause);
    }

}
