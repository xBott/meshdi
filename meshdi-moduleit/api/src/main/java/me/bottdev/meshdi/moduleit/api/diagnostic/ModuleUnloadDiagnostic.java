package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.meshdi.moduleit.api.ModuleState;
import org.semver4j.Semver;

import java.util.Map;
import java.util.Set;

/// Type of [ModuleDiagnostic] for starting of modules.
public sealed interface ModuleUnloadDiagnostic extends Diagnostic permits
        ModuleUnloadDiagnostic.IncorrectState,
        ModuleUnloadDiagnostic.ForgetFailed,
        ModuleUnloadDiagnostic.Unloaded,
        ModuleUnloadDiagnostic.UnloadedN,
        ModuleUnloadDiagnostic.NothingUnloaded,
        ModuleUnloadDiagnostic.Freed,
        ModuleUnloadDiagnostic.Leaked,
        ModuleUnloadDiagnostic.SkippedPersistent,
        ModuleUnloadDiagnostic.LeakCheckDisabled
{

    record IncorrectState(@NonNull String id, @NonNull ModuleState actualState) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.WARN; }
        @Override
        public String type() { return "module_unload_incorrect_state"; }
        @Override
        public String message() { return "Module must be just loaded or stopped: " + id + ". Actual state: " + actualState; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "actual_state", actualState); }
    }

    record ForgetFailed(@NonNull String id, @NonNull Set<String> dependents) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_unload_forget_failed"; }
        @Override
        public String message() { return "Failed to forget module \"" + id + "\" in dependency resolver, because there are modules depending on it: " + String.join(", ", dependents); }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "dependents", dependents); }
    }

    record Unloaded(@NonNull String id, @NonNull Semver version) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_unload_unloaded"; }
        @Override
        public String message() { return "Successfully unloaded module: " + id + " " + version; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "version", version); }
    }

    record UnloadedN(int amount) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_unload_unloaded_n"; }
        @Override
        public String message() { return "Successfully unloaded modules: " + amount + "x"; }
        @Override
        public Map<String, Object> details() { return Map.of("amount", amount); }
    }

    record NothingUnloaded() implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_unload_nothing_unloaded"; }
        @Override
        public String message() { return "No modules unloaded."; }
    }

    record Freed(@NonNull String id) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_unload_freed"; }
        @Override
        public String message() { return "Module classloader successfully freed from JVM heap: " + id; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id); }
    }

    record Leaked(@NonNull String id, @NonNull Throwable cause) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_unload_leaked"; }
        @Override
        public String message() { return "Module classloader leaked in JVM heap: " + id + " (" + cause.getMessage() + ")"; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "cause", cause.getMessage()); }
    }

    record SkippedPersistent(@NonNull String id) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_unload_skipped_persistent"; }
        @Override
        public String message() { return "Module unload skipped (module is persistent): " + id; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id); }
    }

    record LeakCheckDisabled(@NonNull String id) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_unload_leak_check_disabled"; }
        @Override
        public String message() { return "Module classloader leak check disabled: " + id; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id); }
    }
}
