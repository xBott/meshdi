package me.bottdev.meshdi.api.exceptions;

public class BeanCreationException extends DiException {
    public BeanCreationException(String message) {
        super(message);
    }

    public BeanCreationException(String message, Throwable cause) {
      super(message, cause);
    }

}
