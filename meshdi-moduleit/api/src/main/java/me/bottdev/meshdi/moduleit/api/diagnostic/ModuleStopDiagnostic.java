package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.version.SemVersion;

import java.util.Set;

/// Type of [ModuleDiagnostic] for starting of modules.
public sealed interface ModuleStopDiagnostic extends Diagnostic permits
        ModuleStopDiagnostic.NotStarted,
        ModuleStopDiagnostic.MeshUnregisterPlanFailed,
        ModuleStopDiagnostic.MeshUnregisterExecutionFailed,
        ModuleStopDiagnostic.ForgetFailed,
        ModuleStopDiagnostic.Stopped,
        ModuleStopDiagnostic.StoppedN,
        ModuleStopDiagnostic.NothingStopped
{

    static ModuleStopDiagnostic notStarted(
            @NonNull String id
    ) {
        return new NotStarted(id);
    }

    static ModuleStopDiagnostic meshUnregisterPlanFailed(
            @NonNull String id
    ) {
        return new MeshUnregisterPlanFailed(id);
    }

    static ModuleStopDiagnostic meshUnregisterExecuteFailed(
            @NonNull String id
    ) {
        return new MeshUnregisterExecutionFailed(id);
    }

    static ModuleStopDiagnostic forgetFailed(
            @NonNull String id,
            @NonNull Set<String> dependents
    ) {
        return new ForgetFailed(id, dependents);
    }

    static ModuleStopDiagnostic stopped(
            @NonNull String id,
            @NonNull SemVersion version
    ) {
        return new Stopped(id, version);
    }

    static ModuleStopDiagnostic stoppedN(
            int amount
    ) {
        return new StoppedN(amount);
    }

    static ModuleStopDiagnostic nothingStopped() {
        return new NothingStopped();
    }

    record NotStarted(String id) implements ModuleStopDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module \"" + id + "\" is not started.";
        }

    }

    record MeshUnregisterPlanFailed(String id) implements ModuleStopDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to plan unregister context of module in a context mesh: " + id;
        }

    }

    record MeshUnregisterExecutionFailed(String id) implements ModuleStopDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to execute unregister context of module in a context mesh: " + id;
        }

    }

    record ForgetFailed(String id, Set<String> dependents) implements ModuleStopDiagnostic {

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

    record Stopped(String id, SemVersion version) implements ModuleStopDiagnostic {

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
