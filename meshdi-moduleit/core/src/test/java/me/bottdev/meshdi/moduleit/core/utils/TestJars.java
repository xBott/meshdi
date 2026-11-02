package me.bottdev.meshdi.moduleit.core.utils;

import com.google.testing.compile.JavaFileObjects;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/// Builds jars with real classes for class loading tests.
public final class TestJars {

    private TestJars() {}

    /// Compiles all sources together and packs only classes of the given packages,
    /// so a jar can reference classes that live in another jar.
    /// @param sources fully qualified class name -> source code.
    public static byte[] compile(Map<String, String> sources, String... packages) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        InMemoryFileManager fileManager = new InMemoryFileManager(
                compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)
        );
        List<JavaFileObject> files = sources.entrySet().stream()
                .map(entry -> JavaFileObjects.forSourceString(entry.getKey(), entry.getValue()))
                .toList();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean success = compiler.getTask(null, fileManager, diagnostics, List.of("-proc:none"), null, files).call();
        if (!success) {
            throw new IllegalStateException("Test library compilation failed: " + diagnostics.getDiagnostics());
        }

        List<String> prefixes = Arrays.stream(packages).map(pkg -> pkg.replace('.', '/') + "/").toList();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : fileManager.outputs().entrySet()) {
                if (prefixes.stream().noneMatch(entry.getKey()::startsWith)) continue;
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return bytes.toByteArray();
    }

    /// Writes an empty jar, e.g. a module jar without classes.
    public static Path emptyJar(Path file) {
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(file))) {
            return file;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
