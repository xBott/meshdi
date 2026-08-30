package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.version.SemVersion;

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
    ) implements ModuleStopDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to plan unregister context of module in a context mesh: " + id + ". Error: " + error;
        }

    }

    record MeshUnregisterExecutionFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleStopDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to execute unregister context of module in a context mesh: " + id + ". Error: " + error;
        }

    }

    record ForgetFailed(
            @NonNull String id,
            @NonNull Set<String> dependents
    ) implements ModuleStopDiagnostic {

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

    record Stopped(
            @NonNull String id,
            @NonNull SemVersion version
    ) implements ModuleStopDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully stopped module: " + id + " " + version;
        }

    }

    record StoppedN(int amount) implements ModuleStopDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully stopped modules: " + amount + "x";
        }

    }

    record NothingStopped() implements ModuleStopDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "No modules stopped.";
        }

    }

}
