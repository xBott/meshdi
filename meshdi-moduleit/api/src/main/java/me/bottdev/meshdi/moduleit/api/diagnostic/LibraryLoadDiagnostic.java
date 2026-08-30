package me.bottdev.meshdi.moduleit.api.diagnostic;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticType;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;

public sealed interface LibraryLoadDiagnostic extends Diagnostic permits
        LibraryLoadDiagnostic.Requested,
        LibraryLoadDiagnostic.TransitiveDependencyFound,
        LibraryLoadDiagnostic.DependencyExcluded,
        LibraryLoadDiagnostic.VersionConflictResolved,
        LibraryLoadDiagnostic.PomParseFailed,
        LibraryLoadDiagnostic.DownloadStarted,
        LibraryLoadDiagnostic.DownloadCompleted,
        LibraryLoadDiagnostic.DownloadFailed
{

    record Requested(MavenCoordinate coordinate) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticType type() { return DiagnosticType.INFO; }
        @Override
        public String message() { return "Requested library: " + coordinate; }
    }

    record TransitiveDependencyFound(MavenCoordinate parent, MavenCoordinate child) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticType type() { return DiagnosticType.INFO; }
        @Override
        public String message() { return "Found transitive dependency: " + child + " in " + parent; }
    }

    record DependencyExcluded(MavenCoordinate coordinate, String excludedBy) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticType type() { return DiagnosticType.INFO; }
        @Override
        public String message() { return "Excluded dependency " + coordinate + " (excluded by " + excludedBy + ")"; }
    }

    record VersionConflictResolved(MavenCoordinate winning, MavenCoordinate losing) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticType type() { return DiagnosticType.WARN; }
        @Override
        public String message() { return "Version conflict resolved: chosen " + winning + " over " + losing; }
    }

    record PomParseFailed(MavenCoordinate coordinate, String reason) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticType type() { return DiagnosticType.ERROR; }
        @Override
        public String message() { return "Failed to parse POM for " + coordinate + ": " + reason; }
    }

    record DownloadStarted(MavenCoordinate coordinate, String repositoryId) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticType type() { return DiagnosticType.INFO; }
        @Override
        public String message() { return "Started downloading " + coordinate + " from " + repositoryId; }
    }

    record DownloadCompleted(MavenCoordinate coordinate, String repositoryId) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticType type() { return DiagnosticType.INFO; }
        @Override
        public String message() { return "Completed downloading " + coordinate + " from " + repositoryId; }
    }

    record DownloadFailed(MavenCoordinate coordinate, String repositoryId, String reason) implements LibraryLoadDiagnostic {
        @Override
        public DiagnosticType type() { return DiagnosticType.ERROR; }
        @Override
        public String message() { return "Failed downloading " + coordinate + " from " + repositoryId + ": " + reason; }
    }

}
