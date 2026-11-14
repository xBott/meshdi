package me.bottdev.meshdi.api.exceptions;

import lombok.Getter;
import me.bottdev.kern.commons.key.TypedKey;

/// Thrown when a binding is registered for a key that already has a binding in the same context.
public class DuplicateBindingException extends DiRuntimeException {

    @Getter
    private final TypedKey<?> key;

    public DuplicateBindingException(TypedKey<?> key) {
        super("Binding for key " + key + " is already registered. Use override to replace it on purpose.");
        this.key = key;
    }

}
