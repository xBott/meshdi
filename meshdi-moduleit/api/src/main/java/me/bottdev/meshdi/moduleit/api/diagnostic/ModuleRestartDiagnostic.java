package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import org.semver4j.Semver;

import java.util.Map;
import java.util.Set;

/// Type of [ModuleDiagnostic] for starting of modules.
public sealed interface ModuleRestartDiagnostic extends Diagnostic permits
        ModuleRestartDiagnostic.IncorrectState,
        ModuleRestartDiagnostic.MeshUnregisterPlanFailed,
        ModuleRestartDiagnostic.MeshUnregisterExecutionFailed,
        ModuleRestartDiagnostic.ForgetFailed,
        ModuleRestartDiagnostic.BootstrapFailed,
        ModuleRestartDiagnostic.ContextNotStarted,
        ModuleRestartDiagnostic.MeshRegistrationFailed,
        ModuleRestartDiagnostic.Restarted,
        ModuleRestartDiagnostic.RestartedN,
        ModuleRestartDiagnostic.NothingRestarted
{

    record IncorrectState(@NonNull String id) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.WARN; }
        @Override
        public String type() { return "module_restart_incorrect_state"; }
        @Override
        public String message() { return "Module \"" + id + "\" is not started."; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id); }
    }

    record MeshUnregisterPlanFailed(@NonNull String id, @NonNull Throwable cause) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_restart_mesh_unregister_plan_failed"; }
        @Override
        public String message() { return "Failed to plan unregister context of module in a context mesh: " + id + " Error: " + cause; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "cause", cause.getMessage()); }
    }

    record MeshUnregisterExecutionFailed(@NonNull String id, @NonNull Throwable cause) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_restart_mesh_unregister_execution_failed"; }
        @Override
        public String message() { return "Failed to execute unregister context of module in a context mesh: " + id + " Error: " + cause; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "cause", cause.getMessage()); }
    }

    record ForgetFailed(@NonNull String id, @NonNull Set<String> dependents) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_restart_forget_failed"; }
        @Override
        public String message() { return "Failed to forget module \"" + id + "\" in dependency resolver, because there are modules depending on it: " + String.join(", ", dependents); }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "dependents", dependents); }
    }

    record BootstrapFailed(@NonNull String id, @NonNull Throwable cause) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_restart_bootstrap_failed"; }
        @Override
        public String message() { return "Failed to bootstrap context of module: " + id + ". Error: " + cause; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "cause", cause.getMessage()); }
    }

    record ContextNotStarted(@NonNull String id, @NonNull Throwable cause) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_restart_context_not_started"; }
        @Override
        public String message() { return "Failed to start context of module: " + id + ". Error: " + cause; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "cause", cause.getMessage()); }
    }

    record MeshRegistrationFailed(@NonNull String id, @NonNull Throwable cause) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }
        @Override
        public String type() { return "module_restart_mesh_registration_failed"; }
        @Override
        public String message() { return "Failed to register context of module in a context mesh: " + id + ". Error: " + cause; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "cause", cause.getMessage()); }
    }

    record Restarted(@NonNull String id, @NonNull Semver version) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_restart_restarted"; }
        @Override
        public String message() { return "Successfully restarted module: " + id + " " + version; }
        @Override
        public Map<String, Object> details() { return Map.of("module_id", id, "version", version); }
    }

    record RestartedN(int amount) implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_restart_restarted_n"; }
        @Override
        public String message() { return "Successfully restarted modules: " + amount + "x"; }
        @Override
        public Map<String, Object> details() { return Map.of("amount", amount); }
    }

    record NothingRestarted() implements ModuleRestartDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }
        @Override
        public String type() { return "module_restart_nothing_restarted"; }
        @Override
        public String message() { return "No modules restarted."; }
    }
}
