package me.bottdev.meshdi.moduleit.core.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/// A Maven repository in a local directory, reachable through a `file://` URL.
/// Every published file gets a `.sha1` checksum, as on Maven Central.
public final class FakeMavenRepository {

    private final Path root;

    public FakeMavenRepository(Path root) {
        this.root = root;
    }

    public String url() {
        return root.toUri().toString();
    }

    /// Publishes a jar with its POM.
    /// @param gav `groupId:artifactId:version` or `groupId:artifactId:version:classifier`.
    /// @param pomBody XML placed into `<project>` after the coordinates, e.g. [#dependencies(String...)].
    public FakeMavenRepository jar(String gav, String pomBody) {
        String[] parts = gav.split(":");
        String classifier = parts.length > 3 ? parts[3] : null;
        writePom(parts[0], parts[1], parts[2], "jar", pomBody);
        write(artifactPath(parts[0], parts[1], parts[2], classifier, "jar"), ("jar " + gav).getBytes(StandardCharsets.UTF_8));
        return this;
    }

    public FakeMavenRepository jar(String gav) {
        return jar(gav, "");
    }

    /// Publishes a jar with the given content (e.g. from [TestJars#compile]) and its POM.
    public FakeMavenRepository jar(String gav, String pomBody, byte[] content) {
        String[] parts = gav.split(":");
        writePom(parts[0], parts[1], parts[2], "jar", pomBody);
        write(artifactPath(parts[0], parts[1], parts[2], null, "jar"), content);
        return this;
    }

    /// Publishes only a POM with `pom` packaging: a parent, a BOM or a relocation.
    public FakeMavenRepository pom(String gav, String pomBody) {
        String[] parts = gav.split(":");
        writePom(parts[0], parts[1], parts[2], "pom", pomBody);
        return this;
    }

    /// Overwrites a published jar without updating its checksum.
    public void corruptJar(String gav) {
        String[] parts = gav.split(":");
        Path jar = artifactPath(parts[0], parts[1], parts[2], null, "jar");
        try {
            Files.writeString(jar, "tampered");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /// Replaces a published POM with malformed XML. The checksum is updated, so only the content is broken.
    public void breakPom(String gav) {
        String[] parts = gav.split(":");
        write(artifactPath(parts[0], parts[1], parts[2], null, "pom"), "<project><broken".getBytes(StandardCharsets.UTF_8));
    }

    /// Deletes a published POM, keeping the jar.
    public void removePom(String gav) {
        String[] parts = gav.split(":");
        Path pom = artifactPath(parts[0], parts[1], parts[2], null, "pom");
        try {
            Files.delete(pom);
            Files.delete(pom.resolveSibling(pom.getFileName() + ".sha1"));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static String dependencies(String... dependencies) {
        return "<dependencies>" + String.join("", dependencies) + "</dependencies>";
    }

    public static String dependency(String gav) {
        return dependency(gav, "");
    }

    public static String dependency(String gav, String extra) {
        String[] parts = gav.split(":");
        String version = parts.length > 2 ? "<version>" + parts[2] + "</version>" : "";
        return "<dependency><groupId>" + parts[0] + "</groupId><artifactId>" + parts[1] + "</artifactId>"
                + version + extra + "</dependency>";
    }

    public static String scoped(String gav, String scope) {
        return dependency(gav, "<scope>" + scope + "</scope>");
    }

    public static String optional(String gav) {
        return dependency(gav, "<optional>true</optional>");
    }

    public static String parent(String gav) {
        String[] parts = gav.split(":");
        return "<parent><groupId>" + parts[0] + "</groupId><artifactId>" + parts[1] + "</artifactId><version>"
                + parts[2] + "</version></parent>";
    }

    public static String dependencyManagement(String... dependencies) {
        return "<dependencyManagement>" + dependencies(dependencies) + "</dependencyManagement>";
    }

    public static String relocation(String groupId, String artifactId) {
        return "<distributionManagement><relocation><groupId>" + groupId + "</groupId><artifactId>"
                + artifactId + "</artifactId></relocation></distributionManagement>";
    }

    private void writePom(String groupId, String artifactId, String version, String packaging, String body) {
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  <packaging>%s</packaging>
                  %s
                </project>
                """.formatted(groupId, artifactId, version, packaging, body);
        write(artifactPath(groupId, artifactId, version, null, "pom"), pom.getBytes(StandardCharsets.UTF_8));
    }

    private Path artifactPath(String groupId, String artifactId, String version, String classifier, String extension) {
        String fileName = artifactId + "-" + version + (classifier != null ? "-" + classifier : "") + "." + extension;
        String groupPath = Arrays.stream(groupId.split("\\.")).collect(Collectors.joining("/"));
        return root.resolve(groupPath).resolve(artifactId).resolve(version).resolve(fileName);
    }

    private static void write(Path file, byte[] content) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
            Files.writeString(file.resolveSibling(file.getFileName() + ".sha1"), sha1(content));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String sha1(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
