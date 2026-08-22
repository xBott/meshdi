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

    static ModuleRestartDiagnostic incorrectState(
            @NonNull String id
    ) {
        return new IncorrectState(id);
    }

    static ModuleRestartDiagnostic meshUnregisterPlanFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new MeshUnregisterPlanFailed(id, error);
    }

    static ModuleRestartDiagnostic meshUnregisterExecuteFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new MeshUnregisterExecutionFailed(id, error);
    }

    static ModuleRestartDiagnostic forgetFailed(
            @NonNull String id,
            @NonNull Set<String> dependents
    ) {
        return new ForgetFailed(id, dependents);
    }

    static ModuleRestartDiagnostic bootstrapFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new BootstrapFailed(id, error);
    }

    static ModuleRestartDiagnostic contextNotStarted(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new ContextNotStarted(id, error);
    }

    static ModuleRestartDiagnostic meshRegistrationFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new MeshRegistrationFailed(id, error);
    }

    static ModuleRestartDiagnostic restarted(
            @NonNull String id,
            @NonNull SemVersion version
    ) {
        return new Restarted(id, version);
    }

    static ModuleRestartDiagnostic restartedN(
            int amount
    ) {
        return new RestartedN(amount);
    }

    static ModuleRestartDiagnostic nothingRestarted() {
        return new NothingRestarted();
    }

    record IncorrectState(String id) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module \"" + id + "\" is not started.";
        }

    }

    record MeshUnregisterPlanFailed(String id, Throwable error) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to plan unregister context of module in a context mesh: " + id + " Error: " + error;
        }

    }

    record MeshUnregisterExecutionFailed(String id, Throwable error) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to execute unregister context of module in a context mesh: " + id + " Error: " + error;
        }

    }

    record ForgetFailed(String id, Set<String> dependents) implements ModuleRestartDiagnostic {

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

    record BootstrapFailed(String id, Throwable error) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to bootstrap context of module: " + id + ". Error: " + error;
        }

    }

    record ContextNotStarted(String id, Throwable error) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to start context of module: " + id + ". Error: " + error;
        }

    }

    record MeshRegistrationFailed(String id, Throwable error) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to register context of module in a context mesh: " + id + ". Error: " + error;
        }

    }

    record Restarted(String id, SemVersion version) implements ModuleRestartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully restarted module: " + id + " " + version;
        }

    }

    record RestartedN(int amount) implements ModuleRestartDiagnostic {

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
