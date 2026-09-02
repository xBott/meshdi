package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.meshdi.moduleit.api.ModuleState;
import org.semver4j.Semver;

import java.nio.file.Path;
import java.util.Map;

/// Severity of [ModuleDiagnostic] for loading of modules.
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
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.WARN;
        }

        @Override
        public String type() {
            return "module_load_incorrect_state";
        }

        @Override
        public String message() {
            return "Module must be ready: " + id + ". Actual state: " + actualState;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "module_state", actualState
            );
        }
    }

    record ExportsRegistered(@NonNull String id, int amount) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_load_exports_registered";
        }

        @Override
        public String message() {
            return "Registered " + amount + " exports for module: " + id;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "export_amount", amount
            );
        }
    }

    record Loaded(@NonNull String id, @NonNull Semver version) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_load_loaded";
        }

        @Override
        public String message() {
            return "Successfully loaded module: " + id + " " + version;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "module_version", version
            );
        }
    }

    record LoadedN(int amount) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_load_loaded_n";
        }

        @Override
        public String message() {
            return "Successfully loaded modules: " + amount + "x";
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "amount", amount
            );
        }
    }

    record MalformedLibraryUrl(
            @NonNull String id,
            @NonNull Path path,
            @NonNull Throwable cause
    ) implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.ERROR;
        }

        @Override
        public String type() {
            return "module_load_malformed_library_url";
        }

        @Override
        public String message() {
            return "Failed to convert library path to URL for module " + id + " (Path: " + path + "): " + cause.getMessage();
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "library_path", path,
                    "cause", cause.getMessage()
            );
        }
    }

    record NothingLoaded() implements ModuleLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_load_nothing_loaded";
        }

        @Override
        public String message() {
            return "No modules loaded.";
        }
    }

}
