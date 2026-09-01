package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.meshdi.moduleit.api.ModuleState;

/// Type of [ModuleDiagnostic] for starting of modules.
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
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Module must be just loaded or stopped: " + id + ". Actual state: " + actualState;
        }

    }


    record BootstrapFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to bootstrap context of module: " + id + ". Error: " + error;
        }

    }

    record BuildFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to build context of module: " + id + ". Error: " + error;
        }

    }

    record ContextNotStarted(
            @NonNull String id,
            @NonNull Throwable error
    ) implements ModuleStartDiagnostic {

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
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to register context of module in a context mesh: " + id + ". Error: " + error;
        }

    }

    record Started(
            @NonNull String id,
            @NonNull SemVersion version
    ) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully started module: " + id + " " + version;
        }

    }

    record StartedN(int amount) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully started modules: " + amount + "x";
        }

    }

    record NothingStarted() implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "No modules started.";
        }

    }

}
