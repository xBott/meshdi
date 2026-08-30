package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.meshdi.moduleit.api.ModuleState;

import java.util.Set;

/// Type of [ModuleDiagnostic] for starting of modules.
public sealed interface ModuleUnloadDiagnostic extends Diagnostic permits
        ModuleUnloadDiagnostic.IncorrectState,
        ModuleUnloadDiagnostic.ForgetFailed,
        ModuleUnloadDiagnostic.Unloaded,
        ModuleUnloadDiagnostic.UnloadedN,
        ModuleUnloadDiagnostic.NothingUnloaded,
        ModuleUnloadDiagnostic.Freed,
        ModuleUnloadDiagnostic.Leaked,
        ModuleUnloadDiagnostic.SkippedPersistent,
        ModuleUnloadDiagnostic.LeakCheckDisabled
{

    record IncorrectState(
            @NonNull String id,
            @NonNull ModuleState actualState
    ) implements ModuleUnloadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module must be just loaded or stopped: " + id + ". Actual state: " + actualState;
        }

    }

    record ForgetFailed(
            @NonNull String id,
            @NonNull Set<String> dependents
    ) implements ModuleUnloadDiagnostic {

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

    record Unloaded(
            @NonNull String id,
            @NonNull SemVersion version
    ) implements ModuleUnloadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully unloaded module: " + id + " " + version;
        }

    }

    record UnloadedN(int amount) implements ModuleUnloadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully unloaded modules: " + amount + "x";
        }

    }

    record NothingUnloaded() implements ModuleUnloadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "No modules unloaded.";
        }

    }

    record Freed(@NonNull String id) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Module classloader successfully freed from JVM heap: " + id;
        }
    }

    record Leaked(@NonNull String id, @NonNull Throwable error) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Module classloader leaked in JVM heap: " + id + " (" + error.getMessage() + ")";
        }
    }

    record SkippedPersistent(@NonNull String id) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Module unload skipped (module is persistent): " + id;
        }
    }

    record LeakCheckDisabled(@NonNull String id) implements ModuleUnloadDiagnostic {
        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Module classloader leak check disabled: " + id;
        }
    }

}
