package me.bottdev.meshdi.api.exceptions;

public class DiException extends Exception {

    public DiException(String message) {
        super(message);
    }

    public DiException(String message, Throwable cause) {
        super(message, cause);
    }

}
