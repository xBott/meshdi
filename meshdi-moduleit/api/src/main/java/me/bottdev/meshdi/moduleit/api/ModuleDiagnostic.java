package me.bottdev.meshdi.moduleit.api;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.VersionRange;

/// A family of diagnostics related to module system.
public sealed interface ModuleDiagnostic extends Diagnostic permits
        ModuleDiagnostic.AlreadyLoaded,
        ModuleDiagnostic.Duplicate,
        ModuleDiagnostic.ApiVersionMismatch,
        ModuleDiagnostic.BadResolution
{

    static ModuleDiagnostic alreadyLoaded(@NonNull String id) {
        return new AlreadyLoaded(id);
    }

    static ModuleDiagnostic duplicate(@NonNull String id) {
        return new Duplicate(id);
    }

    static ModuleDiagnostic apiVersionMismatch(
            @NonNull String id,
            @NonNull VersionRange range,
            @NonNull SemVersion actual
    ) {
        return new ApiVersionMismatch(id, range, actual);
    }

    static ModuleDiagnostic badResolution(
            @NonNull Diagnostics<DependencyDiagnostic> diagnostics
    ) {
        return new BadResolution(diagnostics);
    }

    record AlreadyLoaded(String id) implements ModuleDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module is already loaded: " + id;
        }

    }

    record Duplicate(String id) implements ModuleDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Found duplicate module: " + id;
        }

    }

    record ApiVersionMismatch(String id, VersionRange range, SemVersion actual) implements ModuleDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "API version mismatch: " + id + " requires API version " + range + ", actual: " + actual;
        }

    }

    record BadResolution(Diagnostics<DependencyDiagnostic> diagnostics) implements ModuleDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Module resolution has failed.\n" + diagnostics;
        }

    }

}
