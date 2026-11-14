package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.LibraryScope;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.semver4j.Semver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleDescriptorCodecTest {

    private static String encodeFull() {
        return ModuleDescriptorCodec.encode(
                "tool",
                "1.2.3",
                ">=1.0.0 <2.0.0",
                List.of("com.example.api", "com.example.spi"),
                List.of(
                        new VersionedDependencyRequest<>("root", DependencyLink.REQUIRED, DependOrder.AFTER, ">=1.0.0"),
                        new VersionedDependencyRequest<>("extra", DependencyLink.OPTIONAL, DependOrder.AFTER, "*")
                ),
                List.of(new RepositoryDeclaration("company", "https://repo.example.com/releases")),
                List.of(
                        new LibraryRequirement("com.example:lib:1.0.0", LibraryScope.SHARED),
                        new LibraryRequirement("com.example:other:2.0.0:linux", LibraryScope.ISOLATED)
                )
        );
    }

    @Test
    @DisplayName("encode → decode keeps every field and the declared order")
    void roundTrip_keepsAllFields() {
        ModuleDescriptor descriptor = ModuleDescriptorCodec.decode(encodeFull());

        assertThat(descriptor.id()).isEqualTo("tool");
        assertThat(descriptor.version()).isEqualTo("1.2.3");
        assertThat(descriptor.apiVersion().isSatisfiedBy(Semver.parse("1.5.0"))).isTrue();
        assertThat(descriptor.apiVersion().isSatisfiedBy(Semver.parse("2.0.0"))).isFalse();
        assertThat(descriptor.exports()).containsExactly("com.example.api", "com.example.spi");
        assertThat(descriptor.getVersionedDependencies()).containsExactly(
                new VersionedDependencyRequest<>("root", DependencyLink.REQUIRED, DependOrder.AFTER, ">=1.0.0"),
                new VersionedDependencyRequest<>("extra", DependencyLink.OPTIONAL, DependOrder.AFTER, "*")
        );
        assertThat(descriptor.repositories())
                .containsExactly(new RepositoryDeclaration("company", "https://repo.example.com/releases"));
        assertThat(descriptor.libraries()).containsExactly(
                new LibraryRequirement("com.example:lib:1.0.0", LibraryScope.SHARED),
                new LibraryRequirement("com.example:other:2.0.0:linux", LibraryScope.ISOLATED)
        );
    }

    @Test
    @DisplayName("encode: the same input always produces the same bytes")
    void encode_isDeterministic() {
        assertThat(encodeFull()).isEqualTo(encodeFull()).doesNotContain("#");
    }

    @Test
    @DisplayName("encode(descriptor) → decode yields an equivalent descriptor")
    void roundTrip_fromDescriptorObject() {
        ModuleDescriptor original = ModuleDescriptorCodec.decode(encodeFull());

        ModuleDescriptor copy = ModuleDescriptorCodec.decode(ModuleDescriptorCodec.encode(original));

        assertThat(copy.id()).isEqualTo(original.id());
        assertThat(copy.semver()).isEqualTo(original.semver());
        assertThat(copy.exports()).isEqualTo(original.exports());
        assertThat(copy.libraries()).isEqualTo(original.libraries());
        assertThat(copy.repositories()).isEqualTo(original.repositories());
        assertThat(copy.getVersionedDependencies()).isEqualTo(original.getVersionedDependencies());
        assertThat(copy.apiVersion().isSatisfiedBy(Semver.parse("1.5.0"))).isTrue();
        assertThat(copy.apiVersion().isSatisfiedBy(Semver.parse("2.0.0"))).isFalse();
    }

    @Test
    @DisplayName("decode: optional sections default to empty and the api version to any")
    void decode_minimalDescriptor() {
        ModuleDescriptor descriptor = ModuleDescriptorCodec.decode("format=1\nid=min\nversion=0.1.0\n");

        assertThat(descriptor.exports()).isEmpty();
        assertThat(descriptor.libraries()).isEmpty();
        assertThat(descriptor.repositories()).isEmpty();
        assertThat(descriptor.getVersionedDependencies()).isEmpty();
        assertThat(descriptor.apiVersion().isSatisfiedBy(Semver.parse("42.0.0"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.example.SomeDescriptor",
            "id=tool\nversion=1.0.0\n",
            "format=2\nid=tool\nversion=1.0.0\n",
            "format=1\nversion=1.0.0\n",
            "format=1\nid=tool\n",
            "format=1\nid=tool\nversion=banana\n",
            "format=1\nid=tool\nversion=1.0.0\ndependencies.0.id=root\ndependencies.0.link=MAYBE\n",
            "format=1\nid=tool\nversion=1.0.0\nlibraries.0.coordinate=a:b:1\nlibraries.0.scope=GLOBAL\n",
            "format=1\nid=tool\nversion=1.0.0\nrepositories.0.id=r\n",
    })
    @DisplayName("decode: invalid content is rejected with a descriptor exception")
    void decode_rejectsInvalidContent(String content) {
        assertThatThrownBy(() -> ModuleDescriptorCodec.decode(content))
                .isInstanceOf(ModuleDescriptorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://repo.example.com/releases",
            "file:///tmp/repo",
            "ftp://repo.example.com",
            "https://",
            "not a url",
            "",
    })
    @DisplayName("isSecure: only well-formed https urls are secure")
    void repositoryDeclaration_rejectsInsecureUrls(String url) {
        assertThat(RepositoryDeclaration.isSecureUrl(url)).isFalse();
    }

    @Test
    void repositoryDeclaration_acceptsHttps() {
        assertThat(new RepositoryDeclaration("r", "https://repo.example.com/releases").isSecure()).isTrue();
    }

}
