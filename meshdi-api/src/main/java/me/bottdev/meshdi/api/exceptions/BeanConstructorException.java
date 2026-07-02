package me.bottdev.meshdi.api.exceptions;

public class BeanConstructorException extends DiException {

    public BeanConstructorException(String message) {
        super(message);
    }

    public BeanConstructorException(String message, Throwable cause) {
        super(message, cause);
    }

}
