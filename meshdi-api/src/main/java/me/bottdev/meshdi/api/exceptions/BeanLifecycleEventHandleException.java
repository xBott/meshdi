package me.bottdev.meshdi.api.exceptions;

public class BeanLifecycleEventHandleException extends BeanLifecycleException {

    public BeanLifecycleEventHandleException(String message) {
        super(message);
    }

    public BeanLifecycleEventHandleException(String message, Throwable cause) {
      super(message, cause);
    }


}
