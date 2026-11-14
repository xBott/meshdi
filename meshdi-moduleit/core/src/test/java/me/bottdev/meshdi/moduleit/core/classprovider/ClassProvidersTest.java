package me.bottdev.meshdi.moduleit.core.classprovider;

import me.bottdev.meshdi.moduleit.api.ModuleClassLoader;
import me.bottdev.meshdi.moduleit.api.classprovider.ClassProviderContainer;
import me.bottdev.meshdi.moduleit.core.SimpleModuleExportRegistry;
import me.bottdev.meshdi.moduleit.core.utils.TestJars;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassProvidersTest {

    @TempDir
    Path tempDir;

    private URL jar(String fileName, Map<String, String> sources, String... packages) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.write(file, TestJars.compile(sources, packages));
        return file.toUri().toURL();
    }

    @Nested
    class Platform {

        final PlatformClassProvider provider = new PlatformClassProvider();

        @Test
        @DisplayName("JDK classes come from the platform loader")
        void providesJdkClasses() throws Exception {
            assertThat(provider.provideClass("java.lang.String")).isSameAs(String.class);
            assertThat(provider.provideClass("javax.sql.DataSource")).isNotNull();
        }

        @Test
        @DisplayName("javax.* and com.sun.* classes that are not in the JDK fall through to the next provider")
        void fallsThroughForLibraryClasses() throws Exception {
            assertThat(provider.provideClass("javax.inject.Inject")).isNull();
            assertThat(provider.provideClass("com.sun.jna.Native")).isNull();
        }

        @Test
        @DisplayName("java.* stays reserved: a miss ends the search")
        void failsForMissingJavaClasses() {
            assertThatThrownBy(() -> provider.provideClass("java.lang.DoesNotExist"))
                    .isInstanceOf(ClassNotFoundException.class);
        }

        @Test
        @DisplayName("a module can load a javax.* class from its own jar")
        void moduleLoadsBundledJavaxClass() throws Exception {
            URL moduleJar = jar("module.jar", Map.of("javax.inject.Marker", """
                    package javax.inject;
                    public class Marker {}
                    """), "javax.inject");

            try (ModuleClassLoader loader = new ModuleClassLoader(
                    "m", new URL[]{moduleJar}, new ClassProviderContainer(List.of(provider)), List.of())) {
                Class<?> marker = loader.loadClass("javax.inject.Marker");
                assertThat(marker.getClassLoader()).isSameAs(loader);
            }
        }

    }

    @Nested
    class Api {

        final ApiClassProvider provider = new ApiClassProvider(
                ClassProvidersTest.class.getClassLoader(),
                Set.of("me.bottdev.meshdi.moduleit.api")
        );

        @Test
        @DisplayName("classes of an api package and its sub-packages are provided")
        void providesApiClasses() throws Exception {
            assertThat(provider.provideClass("me.bottdev.meshdi.moduleit.api.ModuleHandle")).isNotNull();
            assertThat(provider.provideClass("me.bottdev.meshdi.moduleit.api.library.LibraryScope")).isNotNull();
        }

        @Test
        @DisplayName("a package that merely starts with the same characters is not an api package")
        void matchesOnDotBoundary() throws Exception {
            ApiClassProvider kern = new ApiClassProvider(getClass().getClassLoader(), Set.of("me.bottdev.kern"));

            assertThat(kern.provideClass("me.bottdev.kernel.Scheduler")).isNull();
            assertThat(kern.provideClass("me.bottdev.kern.commons.Lazy")).isNotNull();
        }

        @Test
        @DisplayName("classes outside the api packages are not provided, even though the host can load them")
        void hidesNonApiClasses() throws Exception {
            assertThat(provider.provideClass(ClassProvidersTest.class.getName())).isNull();
            assertThat(provider.provideClass("me.bottdev.meshdi.moduleit.core.SimpleModuleManager")).isNull();
        }

        @Test
        @DisplayName("resources are filtered like classes: host files and service files stay hidden")
        void filtersResources() throws Exception {
            assertThat(provider.provideResource("me/bottdev/meshdi/moduleit/api/ModuleHandle.class")).isNotNull();

            assertThat(provider.provideResource("me/bottdev/meshdi/moduleit/core/SimpleModuleManager.class")).isNull();
            assertThat(provider.provideResource("META-INF/MANIFEST.MF")).isNull();
            assertThat(provider.provideResources("META-INF/services/me.bottdev.meshdi.api.ComponentDefinition")).isNull();
        }

    }

    @Nested
    class Exports {

        final SimpleModuleExportRegistry registry = new SimpleModuleExportRegistry();

        @Test
        @DisplayName("exports are served to modules that declared the exporter as a dependency")
        void servesDeclaredDependency() throws Exception {
            try (URLClassLoader exporter = new URLClassLoader(new URL[]{jar("exporter.jar", Map.of("shared.Api", """
                    package shared;
                    public class Api {}
                    """), "shared")}, null)) {
                registry.register("exporter", Set.of("shared"), exporter);

                ExportRegistryClassProvider provider =
                        new ExportRegistryClassProvider("consumer", List.of("exporter"), registry);

                assertThat(provider.provideClass("shared.Api").getClassLoader()).isSameAs(exporter);
            }
        }

        @Test
        @DisplayName("exports of a module that is not a declared dependency are invisible")
        void hidesUndeclaredDependency() throws Exception {
            try (URLClassLoader exporter = new URLClassLoader(new URL[0], null)) {
                registry.register("exporter", Set.of("shared"), exporter);

                ExportRegistryClassProvider provider =
                        new ExportRegistryClassProvider("stranger", List.of(), registry);

                assertThat(provider.provideClass("shared.Api")).isNull();
            }
        }

        @Test
        @DisplayName("a module's own exports are not routed through the registry")
        void skipsOwnExports() throws Exception {
            try (URLClassLoader exporter = new URLClassLoader(new URL[0], null)) {
                registry.register("exporter", Set.of("shared"), exporter);

                ExportRegistryClassProvider provider =
                        new ExportRegistryClassProvider("exporter", List.of("exporter"), registry);

                assertThat(provider.provideClass("shared.Api")).isNull();
            }
        }

    }

}
