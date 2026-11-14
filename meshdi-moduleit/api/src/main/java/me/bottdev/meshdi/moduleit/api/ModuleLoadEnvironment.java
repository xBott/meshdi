package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;
import org.semver4j.Semver;

import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;

/// Data required to open a new module class loader
public interface ModuleLoadEnvironment {

    Semver apiVersion();

    ModuleExportRegistry exportRegistry();

    /// @return A factory capable of creating a diagnostic sink for module diagnostics.
    <D extends Diagnostic> DiagnosticSink<D> createDiagnosticSink();

    /// Creates the class providers every module class loader starts with.
    ///
    /// @param moduleId the module the providers are created for
    /// @param dependencyIds ids of the modules it declared as dependencies; only their exports are visible to it
    /// @param sharedLibraryLoader the class loader of shared libraries
    /// @return the providers, in any order: [ClassProvider#priority()] decides the lookup order
    List<ClassProvider> createBaseProviders(
            String moduleId,
            Collection<String> dependencyIds,
            URLClassLoader sharedLibraryLoader
    );



}
