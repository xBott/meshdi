package me.bottdev.meshdi.moduleit.core;

import lombok.Builder;
import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.kern.commons.diagnostic.DiagnosticSinkFactory;
import me.bottdev.meshdi.moduleit.api.ModuleExportRegistry;
import me.bottdev.meshdi.moduleit.api.ModuleLoadEnvironment;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProvider;
import me.bottdev.meshdi.moduleit.core.classprovider.ApiClassProvider;
import me.bottdev.meshdi.moduleit.core.classprovider.ExportRegistryClassProvider;
import me.bottdev.meshdi.moduleit.core.classprovider.PlatformClassProvider;
import me.bottdev.meshdi.moduleit.core.classprovider.SharedLibraryClassProvider;
import org.semver4j.Semver;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Builder
public record SimpleModuleLoadEnvironment(
        @NonNull Semver apiVersion,
        @NonNull ClassLoader apiLoader,
        @NonNull Set<String> apiPackages,
        @NonNull ModuleExportRegistry exportRegistry,
        @NonNull DiagnosticSinkFactory diagnosticSinkFactory
) implements ModuleLoadEnvironment {

    @Override
    public <D extends Diagnostic> DiagnosticSink<D> createDiagnosticSink() {
        return diagnosticSinkFactory.create();
    }

    @Override
    public List<ClassProvider> createBaseProviders(String moduleId, URLClassLoader sharedLibraryLoader) {
        List<ClassProvider> providers = new ArrayList<>();
        providers.add(new PlatformClassProvider());
        providers.add(new ApiClassProvider(apiLoader, apiPackages));
        providers.add(new ExportRegistryClassProvider(moduleId, exportRegistry));
        providers.add(new SharedLibraryClassProvider(sharedLibraryLoader));
        return providers;
    }
}
