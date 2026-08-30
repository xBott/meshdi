package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.VersionRange;

/// Type of [ModuleDiagnostic] for loading of modules.
public sealed interface ModuleResolutionDiagnostic extends ModuleDiagnostic permits
        ModuleResolutionDiagnostic.AlreadyLoaded,
        ModuleResolutionDiagnostic.Duplicate,
        ModuleResolutionDiagnostic.ApiVersionMismatch,
        ModuleResolutionDiagnostic.BadResolution,
        ModuleResolutionDiagnostic.Resolved
{

    record AlreadyLoaded(@NonNull String id) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Module is already loaded: " + id;
        }

    }

    record Duplicate(@NonNull String id) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "Found duplicate module: " + id;
        }

    }

    record ApiVersionMismatch(
            @NonNull String id,
            @NonNull VersionRange range,
            @NonNull SemVersion actual
    ) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.WARN;
        }

        @Override
        public String message() {
            return "API version mismatch: " + id + " requires API version " + range + ", actual: " + actual;
        }

    }

    record BadResolution(
            @NonNull Diagnostics<DependencyDiagnostic> diagnostics
    ) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.ERROR;
        }

        @Override
        public String message() {
            return "Module resolution has failed.\n" + diagnostics;
        }

    }

    record Resolved(
            @NonNull String id,
            @NonNull SemVersion version
    ) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticType type() {
            return DiagnosticType.INFO;
        }

        @Override
        public String message() {
            return "Successfully resolved module: " + id + " " + version;
        }

    }

}
