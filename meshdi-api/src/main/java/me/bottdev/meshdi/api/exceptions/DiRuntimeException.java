package me.bottdev.meshdi.api.exceptions;

public class DiRuntimeException extends RuntimeException {

    public DiRuntimeException(String message) {
        super(message);
    }

    public DiRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
