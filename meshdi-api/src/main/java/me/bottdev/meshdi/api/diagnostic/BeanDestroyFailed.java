package me.bottdev.meshdi.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.kern.commons.key.TypedKey;

import java.util.Map;

/// Reported when a bean fails during destruction: either its `PRE_DESTROY` handler
/// or its `Disposable.dispose()` threw. Destruction of the remaining beans continues.
///
/// @param key the key of the failed bean
/// @param cause the failure
public record BeanDestroyFailed(
        @NonNull TypedKey<?> key,
        @NonNull Throwable cause
) implements Diagnostic {

    @Override
    public DiagnosticSeverity severity() {
        return DiagnosticSeverity.ERROR;
    }

    @Override
    public String type() {
        return "bean_destroy_failed";
    }

    @Override
    public String message() {
        return "Failed to destroy bean " + key + ". Error: " + cause;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of(
                "bean_key", key.toString(),
                "cause", String.valueOf(cause.getMessage())
        );
    }

}
