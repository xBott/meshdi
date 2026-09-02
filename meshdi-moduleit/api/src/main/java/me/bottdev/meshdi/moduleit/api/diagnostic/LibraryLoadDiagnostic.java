package me.bottdev.meshdi.moduleit.api.diagnostic;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSeverity;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;

import java.util.Map;

public sealed interface LibraryLoadDiagnostic extends Diagnostic permits
        LibraryLoadDiagnostic.Requested,
        LibraryLoadDiagnostic.TransitiveDependencyFound,
        LibraryLoadDiagnostic.DependencyExcluded,
        LibraryLoadDiagnostic.VersionConflictResolved,
        LibraryLoadDiagnostic.PomParseFailed,
        LibraryLoadDiagnostic.VersionUnresolved,
        LibraryLoadDiagnostic.DownloadStarted,
        LibraryLoadDiagnostic.DownloadCompleted,
        LibraryLoadDiagnostic.DownloadFailed
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

    record TransitiveDependencyFound(MavenCoordinate parent, MavenCoordinate child) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }

        @Override
        public String type() {
            return "library_load_transitive_dependency_found";
        }

        @Override
        public String message() { return "Found transitive dependency: " + child + " in " + parent; }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "parent", parent,
                    "child", child
            );
        }
    }

    record DependencyExcluded(MavenCoordinate coordinate, String excludedBy) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.INFO; }

        @Override
        public String type() {
            return "library_load_excluded";
        }

        @Override
        public String message() { return "Excluded dependency " + coordinate + " (excluded by " + excludedBy + ")"; }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate,
                    "excludedBy", excludedBy
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

    record PomParseFailed(MavenCoordinate coordinate, Throwable cause) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }

        @Override
        public String type() {
            return "library_load_pom_parse_failed";
        }

        @Override
        public String message() { return "Failed to parse POM for " + coordinate + ": " + cause.getMessage(); }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate,
                    "cause", cause.getMessage()
            );
        }
    }

    record VersionUnresolved(MavenCoordinate coordinate, String dependency) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticSeverity severity() { return DiagnosticSeverity.ERROR; }

        @Override
        public String type() {
            return "library_load_version_unresolved";
        }

        @Override
        public String message() { return "Unresolved version for dependency: " + coordinate; }

        @Override
        public Map<String, Object> details() {
            return Map.of(
                    "coordinate", coordinate,
                    "dependency", dependency
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

}
