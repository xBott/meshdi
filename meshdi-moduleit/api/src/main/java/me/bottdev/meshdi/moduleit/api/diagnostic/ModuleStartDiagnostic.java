package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.version.SemVersion;

/// Type of [ModuleDiagnostic] for starting of modules.
public sealed interface ModuleStartDiagnostic extends Diagnostic permits
        ModuleStartDiagnostic.BootstrapFailed,
        ModuleStartDiagnostic.ContextNotStarted,
        ModuleStartDiagnostic.MeshRegistrationFailed,
        ModuleStartDiagnostic.Started,
        ModuleStartDiagnostic.StartedN,
        ModuleStartDiagnostic.NothingStarted
{

    static ModuleStartDiagnostic bootstrapFailed(
            @NonNull String id
    ) {
        return new BootstrapFailed(id);
    }

    static ModuleStartDiagnostic contextNotStarted(
            @NonNull String id
    ) {
        return new ContextNotStarted(id);
    }

    static ModuleStartDiagnostic meshRegistrationFailed(
            @NonNull String id
    ) {
        return new MeshRegistrationFailed(id);
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

    record BootstrapFailed(String id) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to bootstrap context of module: " + id;
        }

    }

    record ContextNotStarted(String id) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to start context of module: " + id;
        }

    }

    record MeshRegistrationFailed(String id) implements ModuleStartDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to register context of module in a context mesh: " + id;
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
