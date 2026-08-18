package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.version.SemVersion;

import java.util.Set;

/// Type of [ModuleDiagnostic] for starting of modules.
public sealed interface ModuleUnloadDiagnostic extends Diagnostic permits
        ModuleUnloadDiagnostic.IncorrectState,
        ModuleUnloadDiagnostic.ForgetFailed,
        ModuleUnloadDiagnostic.Unloaded,
        ModuleUnloadDiagnostic.UnloadedN,
        ModuleUnloadDiagnostic.NothingUnloaded
{

    static ModuleUnloadDiagnostic incorrectState(
            @NonNull String id
    ) {
        return new IncorrectState(id);
    }

    static ModuleUnloadDiagnostic forgetFailed(
            @NonNull String id,
            @NonNull Set<String> dependents
    ) {
        return new ForgetFailed(id, dependents);
    }

    static ModuleUnloadDiagnostic unloaded(
            @NonNull String id,
            @NonNull SemVersion version
    ) {
        return new Unloaded(id, version);
    }

    static ModuleUnloadDiagnostic unloadedN(
            int amount
    ) {
        return new UnloadedN(amount);
    }

    static ModuleUnloadDiagnostic nothingUnloaded() {
        return new NothingUnloaded();
    }

    record IncorrectState(String id) implements ModuleUnloadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module \"" + id + "\" must have loaded or stopped state.";
        }

    }

    record ForgetFailed(String id, Set<String> dependents) implements ModuleUnloadDiagnostic {

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

    record Unloaded(String id, SemVersion version) implements ModuleUnloadDiagnostic {

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

}
