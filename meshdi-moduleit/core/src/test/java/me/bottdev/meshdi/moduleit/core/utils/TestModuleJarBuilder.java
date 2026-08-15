package me.bottdev.meshdi.moduleit.core.utils;

import com.google.testing.compile.JavaFileObjects;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class TestModuleJarBuilder {

    private final List<JavaFileObject> sources = new ArrayList<>();
    private final List<Processor> processors;

    private TestModuleJarBuilder(List<Processor> processors) {
        this.processors = processors;
    }

    public static TestModuleJarBuilder withProcessors(Processor... processors) {
        return new TestModuleJarBuilder(List.of(processors));
    }

    public TestModuleJarBuilder withSource(String fqcn, String sourceCode) {
        sources.add(JavaFileObjects.forSourceString(fqcn, sourceCode));
        return this;
    }

    public Path buildTo(Path targetDir, String jarFileName) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        InMemoryFileManager fileManager = new InMemoryFileManager(
            compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)
        );

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, diagnostics, List.of("-proc:only".equals("") ? "" : "-Xlint:none"),
            null, sources
        );
        task.setProcessors(processors);

        boolean success = task.call();
        if (!success) {
            String errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
            throw new IllegalStateException("Test fixture compilation failed:\n" + errors);
        }

        Path jarPath = targetDir.resolve(jarFileName);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (Map.Entry<String, byte[]> entry : fileManager.outputs().entrySet()) {
                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
        }

        diagnostics.getDiagnostics().forEach(d -> System.out.println(d.getKind() + ": " + d.getMessage(null)));

        return jarPath;

    }
}