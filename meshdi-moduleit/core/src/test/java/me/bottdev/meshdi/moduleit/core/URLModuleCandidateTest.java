package me.bottdev.meshdi.moduleit.core;

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
import org.semver4j.range.RangeList;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class URLModuleCandidateTest {

    @TempDir
    Path tempDir;

    // A dummy descriptor available in the test classpath
    public static class DummyDescriptor implements ModuleDescriptor {
        @Override public String id() { return "dummy"; }
        @Override public Semver semver() { return Semver.parse("1.0.0"); }
        @Override public RangeList apiVersion() { return null; }
        @Override public Set<String> exports() { return Set.of(); }
        @Override public Set<RepositoryDeclaration> repositories() { return Set.of(); }
        @Override public Set<LibraryRequirement> libraries() { return Set.of(); }
        @Override public List<VersionedDependencyRequest<String>> getVersionedDependencies() { return List.of(); }
    }

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
        Path jar = createJar("valid.jar", DummyDescriptor.class.getName());
        URL url = jar.toUri().toURL();

        URLModuleCandidate candidate = new URLModuleCandidate(url, jar);

        assertThat(candidate.sourceKey()).isEqualTo("valid.jar");
        assertThat(candidate.sourceUrl()).isEqualTo(url);
        
        ModuleDescriptor descriptor = candidate.descriptor();
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.id()).isEqualTo("dummy");
        assertThat(descriptor.semver().toString()).isEqualTo("1.0.0");
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
    void descriptor_ShouldThrowWhenClassNotFound() throws IOException {
        Path jar = createJar("invalid_class.jar", "com.example.NonExistentDescriptor");
        URL url = jar.toUri().toURL();

        URLModuleCandidate candidate = new URLModuleCandidate(url, jar);

        assertThatThrownBy(candidate::descriptor)
                .isInstanceOf(ModuleDescriptorException.class)
                .hasMessageContaining("Module class 'com.example.NonExistentDescriptor' declared in META-INF/meshdi-module not found");
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
