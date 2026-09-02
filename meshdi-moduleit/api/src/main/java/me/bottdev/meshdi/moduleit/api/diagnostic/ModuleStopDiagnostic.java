package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import org.semver4j.Semver;

import java.util.Map;
import java.util.Set;

/// Type of [ModuleDiagnostic] for starting of modules.
public sealed interface ModuleStopDiagnostic extends Diagnostic permits
        ModuleStopDiagnostic.IncorrectState,
        ModuleStopDiagnostic.MeshUnregisterPlanFailed,
        ModuleStopDiagnostic.MeshUnregisterExecutionFailed,
        ModuleStopDiagnostic.ForgetFailed,
        ModuleStopDiagnostic.Stopped,
        ModuleStopDiagnostic.StoppedN,
        ModuleStopDiagnostic.NothingStopped
{

    record IncorrectState(@NonNull String id) implements ModuleStopDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.WARN; }
        @Override
        public String type() { return "module_stop_incorrect_state"; }
        @Override
        public String message() { return "Module \"" + id + "\" is not started."; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id); }
    }

    record MeshUnregisterPlanFailed(@NonNull String id, @NonNull Throwable cause) implements ModuleStopDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_stop_mesh_unregister_plan_failed"; }
        @Override
        public String message() { return "Failed to plan unregister context of module in a context mesh: " + id + ". Error: " + cause; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "cause", cause.getMessage()); }
    }

    record MeshUnregisterExecutionFailed(@NonNull String id, @NonNull Throwable cause) implements ModuleStopDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_stop_mesh_unregister_execution_failed"; }
        @Override
        public String message() { return "Failed to execute unregister context of module in a context mesh: " + id + ". Error: " + cause; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "cause", cause.getMessage()); }
    }

    record ForgetFailed(@NonNull String id, @NonNull Set<String> dependents) implements ModuleStopDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_stop_forget_failed"; }
        @Override
        public String message() { return "Failed to forget module \"" + id + "\" in dependency resolver, because there are modules depending on it: " + String.join(", ", dependents); }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "dependents", dependents); }
    }

    record Stopped(@NonNull String id, @NonNull Semver version) implements ModuleStopDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_stop_stopped"; }
        @Override
        public String message() { return "Successfully stopped module: " + id + " " + version; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "version", version); }
    }

    record StoppedN(int amount) implements ModuleStopDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_stop_stopped_n"; }
        @Override
        public String message() { return "Successfully stopped modules: " + amount + "x"; }
        @Override
        public Map<String, Object> details() { return Map.of("amount", amount); }
    }

    record NothingStopped() implements ModuleStopDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_stop_nothing_stopped"; }
        @Override
        public String message() { return "No modules stopped."; }
    }
}
