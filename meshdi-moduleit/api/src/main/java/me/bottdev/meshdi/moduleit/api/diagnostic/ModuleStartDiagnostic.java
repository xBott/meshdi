package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.meshdi.moduleit.api.ModuleState;
import org.semver4j.Semver;

import java.util.Map;

/// Severity of [ModuleDiagnostic] for starting of modules.
public sealed interface ModuleStartDiagnostic extends Diagnostic permits
        ModuleStartDiagnostic.IncorrectState,
        ModuleStartDiagnostic.BootstrapFailed,
        ModuleStartDiagnostic.BuildFailed,
        ModuleStartDiagnostic.ContextNotStarted,
        ModuleStartDiagnostic.MeshRegistrationFailed,
        ModuleStartDiagnostic.Started,
        ModuleStartDiagnostic.StartedN,
        ModuleStartDiagnostic.NothingStarted
{

    record IncorrectState(
            @NonNull String id,
            @NonNull ModuleState actualState
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.ERROR;
        }

        @Override
        public String type() {
            return "module_start_incorrect_state";
        }

        @Override
        public String message() {
            return "Module must be just loaded or stopped: " + id + ". Actual state: " + actualState;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "module_state", actualState
            );
        }
    }


    record BootstrapFailed(
            @NonNull String id,
            @NonNull Throwable cause
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.ERROR;
        }

        @Override
        public String type() {
            return "module_start_context_bootstrap_failed";
        }

        @Override
        public String message() {
            return "Failed to bootstrap context of module: " + id + ". Error: " + cause.getMessage();
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "cause", cause.getMessage()
            );
        }

    }

    record BuildFailed(
            @NonNull String id,
            @NonNull Throwable cause
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.ERROR;
        }

        @Override
        public String type() {
            return "module_start_context_build_failed";
        }

        @Override
        public String message() {
            return "Failed to build context of module: " + id + ". Error: " + cause.getMessage();
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "cause", cause.getMessage()
            );
        }

    }

    record ContextNotStarted(
            @NonNull String id,
            @NonNull Throwable cause
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.ERROR;
        }

        @Override
        public String type() {
            return "module_start_context_start_failed";
        }

        @Override
        public String message() {
            return "Failed to start context of module: " + id + ". Error: " + cause.getMessage();
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "cause", cause.getMessage()
            );
        }

    }

    record MeshRegistrationFailed(
            @NonNull String id,
            @NonNull Throwable cause
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.ERROR;
        }

        @Override
        public String type() {
            return "module_start_context_mesh_registration_failed";
        }

        @Override
        public String message() {
            return "Failed to register context of module in a context mesh: " + id + ". Error: " + cause;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "cause", cause.getMessage()
            );
        }

    }

    record Started(
            @NonNull String id,
            @NonNull Semver version
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_start_started";
        }

        @Override
        public String message() {
            return "Successfully started module: " + id + " " + version;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "module_version", version
            );
        }
    }

    record StartedN(int amount) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_start_started_n";
        }

        @Override
        public String message() {
            return "Successfully started modules: " + amount + "x";
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "amount", amount
            );
        }

    }

    record NothingStarted() implements ModuleStartDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_start_nothing_started";
        }

        @Override
        public String message() {
            return "No modules started.";
        }

    }

}
