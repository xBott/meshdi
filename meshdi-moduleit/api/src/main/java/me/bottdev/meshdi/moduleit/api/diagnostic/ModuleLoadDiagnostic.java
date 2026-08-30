package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.meshdi.moduleit.api.ModuleState;

/// Type of [ModuleDiagnostic] for loading of modules.
public sealed interface ModuleLoadDiagnostic extends ModuleDiagnostic permits
        ModuleLoadDiagnostic.IncorrectState,
        ModuleLoadDiagnostic.ExportsRegistered,
        ModuleLoadDiagnostic.Loaded,
        ModuleLoadDiagnostic.LoadedN,
        ModuleLoadDiagnostic.NothingLoaded,
        ModuleLoadDiagnostic.MalformedLibraryUrl
{

    record IncorrectState(
            @NonNull String id,
            @NonNull ModuleState actualState
    ) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module must be ready: " + id + ". Actual state: " + actualState;
        }
    }

    record ExportsRegistered(@NonNull String id, int amount) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Registered " + amount + " exports for module: " + id;
        }
    }

    record Loaded(@NonNull String id, @NonNull SemVersion version) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully loaded module: " + id + " " + version;
        }
    }

    record LoadedN(int amount) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully loaded modules: " + amount + "x";
        }
    }

    record NothingLoaded() implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "No modules loaded.";
        }
    }

    record MalformedLibraryUrl(@NonNull String moduleId, @NonNull java.nio.file.Path path, @NonNull String reason) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Failed to convert library path to URL for module " + moduleId + " (Path: " + path + "): " + reason;
        }
    }

}
