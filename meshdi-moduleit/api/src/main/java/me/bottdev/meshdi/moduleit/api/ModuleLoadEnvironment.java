package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;
import org.semver4j.Semver;

import java.net.URLClassLoader;
import java.util.List;

/// Data required to open a new module class loader
public interface ModuleLoadEnvironment {

    Semver apiVersion();

    ModuleExportRegistry exportRegistry();

    /// @return A factory capable of creating a diagnostic sink for module diagnostics.
    <D extends Diagnostic> DiagnosticSink<D> createDiagnosticSink();

    /// Creates base class providers for a specific module class loader.
    /// @param moduleId The ID of the module.
    /// @param sharedLibraryLoader The URLClassLoader containing shared libraries.
    /// @return A list of base ClassProviders.
    List<ClassProvider> createBaseProviders(String moduleId, URLClassLoader sharedLibraryLoader);


}
