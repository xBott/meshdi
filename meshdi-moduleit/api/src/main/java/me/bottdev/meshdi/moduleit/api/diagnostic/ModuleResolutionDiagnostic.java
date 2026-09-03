package me.bottdev.meshdi.moduleit.api.diagnostic;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.dependency.DependencyDiagnostic;
import org.semver4j.Semver;
import org.semver4j.range.RangeList;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/// Severity of [ModuleDiagnostic] for loading of modules.
public sealed interface ModuleResolutionDiagnostic extends ModuleDiagnostic permits
        ModuleResolutionDiagnostic.AlreadyLoaded,
        ModuleResolutionDiagnostic.Duplicate,
        ModuleResolutionDiagnostic.ApiVersionMismatch,
        ModuleResolutionDiagnostic.BadDependencyResolution,
        ModuleResolutionDiagnostic.Resolved,
        ModuleResolutionDiagnostic.ResolvedN,
        ModuleResolutionDiagnostic.NothingResolved
{

    record AlreadyLoaded(@NonNull String id) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.WARN;
        }

        @Override
        public String type() {
            return "module_resolution_already_loaded";
        }

        @Override
        public String message() {
            return "Module is already loaded: " + id;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id
            );
        }
    }

    record Duplicate(@NonNull String id) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.WARN;
        }

        @Override
        public String type() {
            return "module_resolution_duplicate";
        }

        @Override
        public String message() {
            return "Found duplicate module: " + id;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id
            );
        }

    }

    record ApiVersionMismatch(
            @NonNull String id,
            @NonNull RangeList range,
            @NonNull Semver actual
    ) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.WARN;
        }

        @Override
        public String type() {
            return "module_resolution_version_mismatch";
        }

        @Override
        public String message() {
            return "API version mismatch: " + id + " requires API version " + range + ", actual: " + actual;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "version_range", range,
                    "actual_version", actual
            );
        }

    }

    record BadDependencyResolution(
            @NonNull Diagnostics<DependencyDiagnostic> diagnostics
    ) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.ERROR;
        }

        @Override
        public String type() {
            return "module_resolution_bad_dependency_resolution";
        }

        @Override
        public String message() {
            return "Module dependency resolution has failed:\n" +
                    StreamSupport.stream(diagnostics().spliterator(), false)
                            .map(diagnostic -> " - " + diagnostic)
                            .collect(Collectors.joining("\n"));
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "dependency_diagnostics", diagnostics
            );
        }

    }

    record Resolved(
            @NonNull String id,
            @NonNull Semver version
    ) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_resolution_resolved";
        }

        @Override
        public String message() {
            return "Successfully resolved module: " + id + " " + version;
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "module_id", id,
                    "module_version", version
            );
        }

    }

    record ResolvedN(
            int amount
    ) implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_resolution_resolved_n";
        }

        @Override
        public String message() {
            return "Successfully resolved modules: " + amount + "x";
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "amount", amount
            );
        }

    }

    record NothingResolved() implements ModuleResolutionDiagnostic {

        @Override
        public DiagnosticSeverity severity() {
            return DiagnosticSeverity.INFO;
        }

        @Override
        public String type() {
            return "module_resolution_nothing_resolved";
        }

        @Override
        public String message() {
            return "No modules resolved.";
        }

    }

}
