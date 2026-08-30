package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.version.SemVersion;

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
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module \"" + id + "\" is not started.";
        }

    }

    record MeshUnregisterPlanFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to plan unregister context of module in a context mesh: " + id + " Error: " + error;
        }

    }

    record MeshUnregisterExecutionFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to execute unregister context of module in a context mesh: " + id + " Error: " + error;
        }

    }

    record ForgetFailed(
            @NonNull String id,
            @NonNull Set<String> dependents
    ) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to forget module \"" + id + "\" in dependency resolver, because there are modules depending on it: " +
                    String.join(", ", dependents);
        }

    }

    record BootstrapFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to bootstrap context of module: " + id + ". Error: " + error;
        }

    }

    record ContextNotStarted(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to start context of module: " + id + ". Error: " + error;
        }

    }

    record MeshRegistrationFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to register context of module in a context mesh: " + id + ". Error: " + error;
        }

    }

    record Restarted(
            @NonNull String id,
            @NonNull SemVersion version
    ) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully restarted module: " + id + " " + version;
        }

    }

    record RestartedN(
            int amount
    ) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully restarted modules: " + amount + "x";
        }

    }

    record NothingRestarted() implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "No modules restarted.";
        }

    }

}
