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
        ModuleStartDiagnostic.ContextNotStarted,
        ModuleStartDiagnostic.MeshRegistrationFailed,
        ModuleStartDiagnostic.Started,
        ModuleStartDiagnostic.StartedN,
        ModuleStartDiagnostic.NothingStarted
{

    static ModuleStartDiagnostic incorrectState(
            @NonNull String id,
            @NonNull ModuleState actualState
    ) {
        return new IncorrectState(id, actualState);
    }

    static ModuleStartDiagnostic bootstrapFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new BootstrapFailed(id, error);
    }

    static ModuleStartDiagnostic contextNotStarted(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new ContextNotStarted(id, error);
    }

    static ModuleStartDiagnostic meshRegistrationFailed(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new MeshRegistrationFailed(id, error);
    }

    static ModuleStartDiagnostic started(
            @NonNull String id,
            @NonNull SemVersion version
    ) {
        return new Started(id, version);
    }

    static ModuleStartDiagnostic startedN(
            int amount
    ) {
        return new StartedN(amount);
    }

    static ModuleStartDiagnostic nothingStarted() {
        return new NothingStarted();
    }

    record IncorrectState(String id, ModuleState actualState) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Module must be just loaded or stopped: " + id + ". Actual state: " + actualState;
        }

    }


    record BootstrapFailed(String id, Throwable error) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to bootstrap context of module: " + id + ". Error: " + error;
        }

    }

    record ContextNotStarted(String id, Throwable error) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to start context of module: " + id + ". Error: " + error;
        }

    }

    record MeshRegistrationFailed(String id, Throwable error) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to register context of module in a context mesh: " + id + ". Error: " + error;
        }

    }

    record Started(String id, SemVersion version) implements ModuleStartDiagnostic {

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
