package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.VersionRange;

/// Type of [ModuleDiagnostic] for loading of modules.
public sealed interface ModuleLoadDiagnostic extends ModuleDiagnostic permits
        ModuleLoadDiagnostic.AlreadyLoaded,
        ModuleLoadDiagnostic.Duplicate,
        ModuleLoadDiagnostic.ApiVersionMismatch,
        ModuleLoadDiagnostic.BadResolution,
        ModuleLoadDiagnostic.Loaded
{

    static ModuleLoadDiagnostic alreadyLoaded(@NonNull String id) {
        return new AlreadyLoaded(id);
    }

    static ModuleLoadDiagnostic duplicate(@NonNull String id) {
        return new Duplicate(id);
    }

    static ModuleLoadDiagnostic apiVersionMismatch(
            @NonNull String id,
            @NonNull VersionRange range,
            @NonNull SemVersion actual
    ) {
        return new ApiVersionMismatch(id, range, actual);
    }

    static ModuleLoadDiagnostic badResolution(
            @NonNull Diagnostics<DependencyDiagnostic> diagnostics
    ) {
        return new BadResolution(diagnostics);
    }

    static ModuleLoadDiagnostic loaded(
            @NonNull String id,
            @NonNull SemVersion version
    ) {
        return new Loaded(id, version);
    }

    record AlreadyLoaded(String id) implements ModuleLoadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module is already loaded: " + id;
        }

    }

    record Duplicate(String id) implements ModuleLoadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Found duplicate module: " + id;
        }

    }

    record ApiVersionMismatch(String id, VersionRange range, SemVersion actual) implements ModuleLoadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "API version mismatch: " + id + " requires API version " + range + ", actual: " + actual;
        }

    }

    record BadResolution(Diagnostics<DependencyDiagnostic> diagnostics) implements ModuleLoadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Module resolution has failed.\n" + diagnostics;
        }

    }

    record Loaded(String id, SemVersion version) implements ModuleLoadDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully loaded module: " + id + " " + version;
        }

    }

}
