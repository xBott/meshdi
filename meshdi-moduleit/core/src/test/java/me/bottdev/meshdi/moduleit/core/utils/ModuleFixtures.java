package me.bottdev.meshdi.moduleit.core.utils;

import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.kern.commons.diagnostic.DiagnosticSinkFactory;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.StatefulVersionedDependencyResolver;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.kern.dependency.versioned.graph.GraphStatefulVersionedDependencyResolver;
import me.bottdev.kern.struct.algorithms.cycle.SimpleCycleDetector;
import me.bottdev.kern.struct.algorithms.sort.KahnSorter;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleLibrariesResult;
import me.bottdev.meshdi.moduleit.api.ModuleLoadEnvironment;
import me.bottdev.meshdi.moduleit.core.SimpleModuleExportRegistry;
import me.bottdev.meshdi.moduleit.core.SimpleModuleLoadEnvironment;
import me.bottdev.meshdi.moduleit.core.VirtualModuleDescriptor;
import me.bottdev.meshdi.moduleit.core.VirtualModuleHandle;
import org.semver4j.Semver;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/// Shared builders for module manager tests.
public final class ModuleFixtures {

    public static final Semver API_VERSION = Objects.requireNonNull(Semver.parse("1.0.0"));
    public static final Semver MODULE_VERSION = Objects.requireNonNull(Semver.parse("1.0.0"));

    public static final ModuleLibrariesResult NO_LIBRARIES =
            new ModuleLibrariesResult(List.of(), Map.of(), false);

    private ModuleFixtures() {}

    /// A candidate that is not an internal candidate, so the manager wraps it into a `SimpleModuleHandle`.
    public record PlainCandidate(ModuleDescriptor descriptor) implements ModuleCandidate {

        @Override
        public String sourceKey() {
            return "plain:" + descriptor.id();
        }

        @Override
        public URL sourceUrl() {
            return null;
        }

    }

    public static VirtualModuleDescriptor descriptor(String id, String... dependencies) {
        return VirtualModuleDescriptor.builder()
                .id(id)
                .semver(MODULE_VERSION)
                .versionedDependencies(Arrays.stream(dependencies).map(ModuleFixtures::requires).toList())
                .build();
    }

    public static VersionedDependencyRequest<String> requires(String id) {
        return new VersionedDependencyRequest<>(id, DependencyLink.REQUIRED, DependOrder.AFTER, ">=1.0.0");
    }

    public static Context mockContext(String id) {
        Context context = mock(Context.class);
        lenient().when(context.id()).thenReturn(id);
        return context;
    }

    public static VirtualModuleHandle virtual(String id, String... dependencies) {
        return new VirtualModuleHandle(descriptor(id, dependencies), mockContext(id));
    }

    public static VirtualModuleHandle persistent(String id) {
        return VirtualModuleHandle.builder()
                .descriptor(descriptor(id))
                .context(mockContext(id))
                .persistent(true)
                .build();
    }

    public static StatefulVersionedDependencyResolver<String, ModuleCandidate> realResolver() {
        return new GraphStatefulVersionedDependencyResolver<>(new KahnSorter(new SimpleCycleDetector()));
    }

    /// @return environment with api version `1.0.0` that forwards every diagnostic into `diagnostics`.
    public static ModuleLoadEnvironment environment(List<Diagnostic> diagnostics) {
        return SimpleModuleLoadEnvironment.builder()
                .apiVersion(API_VERSION)
                .apiLoader(ModuleFixtures.class.getClassLoader())
                .apiPackages(Set.of())
                .exportRegistry(new SimpleModuleExportRegistry())
                .diagnosticSinkFactory(new DiagnosticSinkFactory() {
                    @Override
                    public <D extends Diagnostic> DiagnosticSink<D> create() {
                        return DiagnosticSink.forwarding(diagnostics::add);
                    }
                })
                .build();
    }

}
