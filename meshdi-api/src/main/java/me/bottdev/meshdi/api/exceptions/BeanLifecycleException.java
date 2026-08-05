package me.bottdev.meshdi.api.exceptions;

public class BeanLifecycleException extends DiException {

    public BeanLifecycleException(String message) {
        super(message);
    }

    public BeanLifecycleException(String message, Throwable cause) {
      super(message, cause);
    }

}
