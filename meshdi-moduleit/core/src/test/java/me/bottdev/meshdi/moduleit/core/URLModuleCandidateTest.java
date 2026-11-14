package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptorCodec;
import me.bottdev.meshdi.moduleit.api.library.LibraryScope;
import java.util.concurrent.atomic.AtomicBoolean;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semver4j.Semver;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class URLModuleCandidateTest {

    @TempDir
    Path tempDir;

    private static final String VALID_DESCRIPTOR = """
            format=1
            id=dummy
            version=1.0.0
            apiVersion=>=1.0.0
            exports.0=com.example.api
            dependencies.0.id=root
            dependencies.0.version=>=2.0.0
            dependencies.0.link=OPTIONAL
            dependencies.0.order=AFTER
            repositories.0.id=company
            repositories.0.url=https://repo.example.com/releases
            libraries.0.coordinate=com.example:lib:1.0.0
            libraries.0.scope=ISOLATED
            """;

    /// Loaded only if the candidate executes code named in the descriptor.
    public static class Tripwire {
        static { TRIPWIRE_LOADED.set(true); }
    }

    private static final AtomicBoolean TRIPWIRE_LOADED = new AtomicBoolean(false);

    private Path createJar(String fileName, String descriptorContent) throws IOException {
        Path jarPath = tempDir.resolve(fileName);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            if (descriptorContent != null) {
                jos.putNextEntry(new JarEntry(ModuleDescriptor.DESCRIPTOR_FILE));
                jos.write(descriptorContent.getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
        return jarPath;
    }

    @Test
    void descriptor_ShouldLoadValidDescriptor() throws IOException {
        Path jar = createJar("valid.jar", VALID_DESCRIPTOR);
        URL url = jar.toUri().toURL();

        URLModuleCandidate candidate = new URLModuleCandidate(url, jar);

        assertThat(candidate.sourceKey()).isEqualTo("valid.jar");
        assertThat(candidate.sourceUrl()).isEqualTo(url);

        ModuleDescriptor descriptor = candidate.descriptor();
        assertThat(descriptor.id()).isEqualTo("dummy");
        assertThat(descriptor.semver().toString()).isEqualTo("1.0.0");
        assertThat(descriptor.apiVersion().isSatisfiedBy(Semver.parse("1.2.0"))).isTrue();
        assertThat(descriptor.exports()).containsExactly("com.example.api");
        assertThat(descriptor.repositories())
                .containsExactly(new RepositoryDeclaration("company", "https://repo.example.com/releases"));
        assertThat(descriptor.libraries())
                .containsExactly(new LibraryRequirement("com.example:lib:1.0.0", LibraryScope.ISOLATED));
        assertThat(descriptor.getVersionedDependencies()).containsExactly(
                new VersionedDependencyRequest<>("root", DependencyLink.OPTIONAL, DependOrder.AFTER, ">=2.0.0")
        );
    }

    @Test
    void descriptor_ShouldNeverLoadClassesNamedInDescriptor() throws IOException {
        // The old format: the file holds a class name that used to be instantiated during discovery.
        Path jar = createJar("legacy.jar", Tripwire.class.getName());
        URLModuleCandidate candidate = new URLModuleCandidate(jar.toUri().toURL(), jar);

        assertThatThrownBy(candidate::descriptor)
                .isInstanceOf(ModuleDescriptorException.class)
                .hasMessageContaining("no 'format' entry");
        assertThat(TRIPWIRE_LOADED).isFalse();
    }

    @Test
    void descriptor_ShouldThrowOnUnsupportedFormat() throws IOException {
        Path jar = createJar("future.jar", "format=99\nid=dummy\nversion=1.0.0\n");
        URLModuleCandidate candidate = new URLModuleCandidate(jar.toUri().toURL(), jar);

        assertThatThrownBy(candidate::descriptor)
                .isInstanceOf(ModuleDescriptorException.class)
                .hasMessageContaining("Unsupported module descriptor format");
    }

    @Test
    void descriptor_ShouldThrowOnInvalidVersion() throws IOException {
        Path jar = createJar("badversion.jar", "format=1\nid=dummy\nversion=not-a-version\n");
        URLModuleCandidate candidate = new URLModuleCandidate(jar.toUri().toURL(), jar);

        assertThatThrownBy(candidate::descriptor)
                .isInstanceOf(ModuleDescriptorException.class)
                .hasMessageContaining("invalid version");
    }

    @Test
    void descriptor_ShouldThrowWhenTooLarge() throws IOException {
        String huge = "format=1\nid=dummy\nversion=1.0.0\njunk=" + "x".repeat(ModuleDescriptorCodec.MAX_SIZE_BYTES);
        Path jar = createJar("huge.jar", huge);
        URLModuleCandidate candidate = new URLModuleCandidate(jar.toUri().toURL(), jar);

        assertThatThrownBy(candidate::descriptor)
                .isInstanceOf(ModuleDescriptorException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void descriptor_ShouldThrowWhenDescriptorFileIsMissing() throws IOException {
        Path jar = createJar("missing.jar", null); // No META-INF/meshdi-module
        URL url = jar.toUri().toURL();

        URLModuleCandidate candidate = new URLModuleCandidate(url, jar);

        assertThatThrownBy(candidate::descriptor)
                .isInstanceOf(ModuleDescriptorException.class)
                .hasMessageContaining("Missing META-INF/meshdi-module in jar");
    }

    @Test
    void descriptor_ShouldThrowWhenDescriptorFileIsEmpty() throws IOException {
        Path jar = createJar("empty.jar", "   \n"); // Empty content after trim
        URL url = jar.toUri().toURL();

        URLModuleCandidate candidate = new URLModuleCandidate(url, jar);

        assertThatThrownBy(candidate::descriptor)
                .isInstanceOf(ModuleDescriptorException.class)
                .hasMessageContaining("META-INF/meshdi-module is empty in jar");
    }

    @Test
    void descriptor_ShouldThrowWhenFileIsNotAJar() throws IOException {
        Path textFile = tempDir.resolve("not_a_jar.txt");
        Files.writeString(textFile, "just some text");
        URL url = textFile.toUri().toURL();

        URLModuleCandidate candidate = new URLModuleCandidate(url, textFile);

        assertThatThrownBy(candidate::descriptor)
                .isInstanceOf(ModuleDescriptorException.class)
                .hasMessageContaining("Failed to read META-INF/meshdi-module from");
    }
}
