package me.bottdev.meshdi.api.exceptions;

public class BeanAutowireException extends DiRuntimeException {

    public BeanAutowireException(String message) {
        super(message);
    }

    public BeanAutowireException(String message, Throwable cause) {
        super(message, cause);
    }

}
