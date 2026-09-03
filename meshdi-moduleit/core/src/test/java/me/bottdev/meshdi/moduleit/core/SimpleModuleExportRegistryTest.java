package me.bottdev.meshdi.moduleit.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleModuleExportRegistryTest {

    private SimpleModuleExportRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleModuleExportRegistry();
    }

    @Test
    void register_ShouldAddModuleAndPackages() {
        ClassLoader loader = new URLClassLoader(new URL[0]);
        Set<String> packages = Set.of("me.bottdev.test", "me.bottdev.api");

        registry.register("module1", packages, loader);

        assertThat(registry.isRegistered("module1")).isTrue();
        assertThat(registry.getPackagesById("module1")).containsExactlyInAnyOrderElementsOf(packages);
        assertThat(registry.getClassLoaderById("module1")).isSameAs(loader);
        
        assertThat(registry.getIdByPackage("me.bottdev.test")).isEqualTo("module1");
        assertThat(registry.getIdByPackage("me.bottdev.api")).isEqualTo("module1");
    }

    @Test
    void register_ShouldIgnoreIfAlreadyRegistered() {
        ClassLoader loader1 = new URLClassLoader(new URL[0]);
        ClassLoader loader2 = new URLClassLoader(new URL[0]);

        registry.register("module1", Set.of("pkg1"), loader1);
        registry.register("module1", Set.of("pkg2"), loader2);

        assertThat(registry.getPackagesById("module1")).containsExactly("pkg1");
        assertThat(registry.getClassLoaderById("module1")).isSameAs(loader1);
        assertThat(registry.getIdByPackage("pkg1")).isEqualTo("module1");
        assertThat(registry.getIdByPackage("pkg2")).isNull();
    }

    @Test
    void unregister_ShouldRemoveModuleAndPackages() {
        ClassLoader loader = new URLClassLoader(new URL[0]);
        registry.register("module1", Set.of("pkg1", "pkg2"), loader);

        registry.unregister("module1");

        assertThat(registry.isRegistered("module1")).isFalse();
        assertThat(registry.getPackagesById("module1")).isEmpty();
        assertThat(registry.getClassLoaderById("module1")).isNull();
        assertThat(registry.getIdByPackage("pkg1")).isNull();
        assertThat(registry.getIdByPackage("pkg2")).isNull();
    }

    @Test
    void unregister_ShouldDoNothingIfNotRegistered() {
        // Just verify no exception is thrown
        registry.unregister("non-existent-module");
    }

    @Test
    void getters_ShouldReturnDefaultValuesForUnknownIds() {
        assertThat(registry.isRegistered("unknown")).isFalse();
        assertThat(registry.getPackagesById("unknown")).isEmpty();
        assertThat(registry.getClassLoaderById("unknown")).isNull();
        assertThat(registry.getIdByPackage("unknown")).isNull();
    }
}
