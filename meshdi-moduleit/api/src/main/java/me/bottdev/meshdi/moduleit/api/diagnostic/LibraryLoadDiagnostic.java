package me.bottdev.meshdi.moduleit.api.diagnostic;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;

import java.util.Map;

public sealed interface LibraryLoadDiagnostic extends Diagnostic permits
        LibraryLoadDiagnostic.Requested,
        LibraryLoadDiagnostic.VersionConflictResolved,
        LibraryLoadDiagnostic.DownloadStarted,
        LibraryLoadDiagnostic.DownloadCompleted,
        LibraryLoadDiagnostic.DownloadFailed,
        LibraryLoadDiagnostic.ResolutionFailed,
        LibraryLoadDiagnostic.Resolved,
        LibraryLoadDiagnostic.DescriptorUnavailable
{

    record Requested(MavenCoordinate coordinate) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }

        @Override
        public String type() {
            return "library_load_requested";
        }

        @Override
        public String message() { return "Requested library: " + coordinate; }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate
            );
        }
    }

    record VersionConflictResolved(MavenCoordinate winning, MavenCoordinate losing) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.WARN; }

        @Override
        public String type() {
            return "library_load_conflict_resolved";
        }

        @Override
        public String message() { return "Version conflict resolved: chosen " + winning + " over " + losing; }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "winning", winning,
                    "losing", losing
            );
        }
    }

    record DownloadStarted(MavenCoordinate coordinate, String repositoryId) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }

        @Override
        public String type() {
            return "library_load_download_started";
        }

        @Override
        public String message() { return "Started downloading " + coordinate + " from " + repositoryId; }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate,
                    "repositoryId", repositoryId
            );
        }
    }

    record DownloadCompleted(MavenCoordinate coordinate, String repositoryId) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }

        @Override
        public String type() {
            return "library_load_download_completed";
        }

        @Override
        public String message() { return "Completed downloading " + coordinate + " from " + repositoryId; }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate,
                    "repositoryId", repositoryId
            );
        }
    }

    record DownloadFailed(MavenCoordinate coordinate, String repositoryId, Throwable cause) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }

        @Override
        public String type() {
            return "library_load_download_failed";
        }

        @Override
        public String message() { return "Failed downloading " + coordinate + " from " + repositoryId + ": " + cause.getMessage(); }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate,
                    "repositoryId", repositoryId,
                    "cause", cause.getMessage()
            );
        }
    }

    /// The dependency graph could not be built, e.g. a POM is missing or a version cannot be resolved.
    /// @param requestedBy who requested the libraries: `shared` or a module id.
    record ResolutionFailed(String requestedBy, Throwable cause) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }

        @Override
        public String type() {
            return "library_load_resolution_failed";
        }

        @Override
        public String message() { return "Failed to resolve libraries of " + requestedBy + ": " + cause.getMessage(); }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "requestedBy", requestedBy,
                    "cause", String.valueOf(cause.getMessage())
            );
        }
    }

    /// The library is part of the resolved class path.
    /// @param requestedBy who requested the libraries: `shared` or a module id.
    record Resolved(MavenCoordinate coordinate, String requestedBy) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }

        @Override
        public String type() {
            return "library_load_resolved";
        }

        @Override
        public String message() { return "Resolved " + coordinate + " for " + requestedBy; }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate,
                    "requestedBy", requestedBy
            );
        }
    }

    /// The POM of a library is missing or invalid. The library itself is still loaded,
    /// but its transitive dependencies are unknown and therefore not loaded.
    record DescriptorUnavailable(MavenCoordinate coordinate, String reason) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.WARN; }

        @Override
        public String type() {
            return "library_load_descriptor_unavailable";
        }

        @Override
        public String message() {
            return "POM of " + coordinate + " is unavailable (" + reason + "), its transitive dependencies are not loaded";
        }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate,
                    "reason", reason
            );
        }
    }

}
