package me.bottdev.meshdi.api.exceptions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.bottdev.kern.commons.key.TypedKey;

@RequiredArgsConstructor
public class BeanResolvationException extends RuntimeException {

    @Getter
    private final TypedKey<?> key;

    public BeanResolvationException(TypedKey<?> key, String message) {
        super(message);
        this.key = key;
    }

    public BeanResolvationException(TypedKey<?> key, String message, Throwable cause) {
      super(message, cause);
      this.key = key;
    }

}
