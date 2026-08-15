package me.bottdev.meshdi.moduleit.processor;

import com.google.auto.service.AutoService;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.annotations.Module;
import com.squareup.javapoet.*;
import me.bottdev.kern.meta.apt.AbstractMetaProcessor;
import me.bottdev.kern.meta.core.FileWriter;
import me.bottdev.kern.meta.core.MessageType;
import me.bottdev.kern.meta.core.configuration.ProcessorConfigurationBuilder;
import me.bottdev.kern.meta.core.models.ModelKind;
import me.bottdev.kern.meta.core.models.type.ClassModel;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.SemVersionParser;
import me.bottdev.kern.version.VersionRange;
import me.bottdev.kern.version.VersionRangeParser;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Modifier;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static me.bottdev.kern.meta.core.diagnostic.DiagnosticRuleResult.error;
import static me.bottdev.kern.meta.core.diagnostic.DiagnosticRuleResult.ok;

@AutoService(Processor.class)
public class ModuleMetaProcessor extends AbstractMetaProcessor {

    private static final String DESCRIPTOR_SUFFIX = "Descriptor";

    private record DependencyRepresentation(
            String id,
            DependencyLink link,
            DependOrder order,
            String version
    ) {}

    private record ModuleRepresentation(
            ClassModel model,
            String id,
            String version,
            String apiVersion,
            List<DependencyRepresentation> dependencies,
            String[] exports
    ) {}

    private ModuleRepresentation discovered = null;

    @Override
    protected void configure(ProcessorConfigurationBuilder builder) {

        builder.select(ModelKind.CLASS, Module.class)
                .map(model -> {
                    Module module = model.annotation(Module.class).orElseThrow();
                    String id = module.id();
                    String version = module.version();
                    String apiVersion = module.apiVersion();

                    List<DependencyRepresentation> dependencies = Arrays.stream(module.dependencies())
                            .map(dependsOn -> new DependencyRepresentation(
                                    dependsOn.id(),
                                    dependsOn.link(),
                                    dependsOn.order(),
                                    dependsOn.version()
                                    )
                            ).toList();

                    String[] exports = module.exports();

                    return new ModuleRepresentation(model, id, version, apiVersion, dependencies, exports);

                })
                .validate(rules -> rules
                        .rule(_ -> discovered == null ? ok() : error("Project can contain only one module."))
                        .rule(representation -> {
                            try {
                                SemVersionParser.parse(representation.version());
                                return ok();

                            } catch (Exception ex) {
                                return error(
                                        "Module version has incorrect format: " +
                                                representation.version() +
                                                ". Follow SemVer format: "
                                );

                            }
                        })
                        .rule(representation -> {
                            try {
                                VersionRangeParser.parse(representation.apiVersion());
                                return ok();

                            } catch (Exception ex) {
                                return error(
                                        "Module api version range has incorrect format: " +
                                                representation.apiVersion() +
                                                ". Follow SemVer format: "
                                );

                            }
                        })
                        .rule(representation -> {
                            for (DependencyRepresentation dependency : representation.dependencies()) {
                                try {
                                    VersionRangeParser.parse(dependency.version());

                                } catch (Exception ex) {
                                    return error(
                                            "Version range of module's dependency \"" +
                                                    dependency.id() +
                                                    "\" has incorrect format: " +
                                                    dependency.version() +
                                                    ". Follow SemVer format."
                                    );

                                }
                            }
                            return ok();
                        })
                )
                .run(representation -> discovered = representation);

        builder.afterAll()
                .generate(() -> {
                    generateDescriptor(discovered);
                    generateManifest(discovered);
                });

    }

    private void generateDependencies(
            ModuleRepresentation representation,
            TypeSpec.Builder typeSpec
    ) {

        List<DependencyRepresentation> dependencies = representation.dependencies();
        ParameterizedTypeName requestClassName = ParameterizedTypeName.get(
                VersionedDependencyRequest.class,
                String.class
        );
        ParameterizedTypeName dependenciesClassName = ParameterizedTypeName.get(
                ClassName.get(List.class),
                requestClassName
        );

        typeSpec.addField(
                FieldSpec.builder(dependenciesClassName, "dependencies")
                        .addModifiers(Modifier.PRIVATE)
                        .initializer("null")
                        .build()
        );

        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder("getVersionedDependencies")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .beginControlFlow("if (dependencies == null)");

        StringBuilder listOfArguments = new StringBuilder();
        for (int i = 0; i < dependencies.size(); i++) {
            DependencyRepresentation dependency = dependencies.get(i);
            String dependencyName = "dependency" + i;
            methodSpec.addStatement("$T $L = new $T($S, $T.$L, $T.$L, $T.parse($S))",
                    requestClassName,
                    dependencyName,
                    requestClassName,
                    dependency.id(),
                    DependencyLink.class,
                    dependency.link(),
                    DependOrder.class,
                    dependency.order(),
                    VersionRangeParser.class,
                    dependency.version()
            );

            listOfArguments.append(dependencyName);
            if (i < dependencies.size() - 1) listOfArguments.append(", ");
        }

        methodSpec
                .addStatement("dependencies = $T.of($L)", List.class, listOfArguments.toString())
                .endControlFlow()
                .addStatement("return dependencies");

        methodSpec.returns(dependenciesClassName);

        typeSpec.addMethod(methodSpec.build());
    }

    private void generateExports(
            ModuleRepresentation representation,
            TypeSpec.Builder typeSpec
    ) {

        String[] exports = representation.exports();
        ParameterizedTypeName stringSetClassName = ParameterizedTypeName.get(Set.class, String.class);

        typeSpec.addField(
                FieldSpec.builder(stringSetClassName, "exports")
                        .addModifiers(Modifier.PRIVATE)
                        .initializer("null")
                        .build()
        );

        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder("exports")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .beginControlFlow("if (exports == null)");

        StringBuilder setOfArguments = new StringBuilder();
        for (int i = 0; i < exports.length; i++) {
            String exported = exports[i];
            String packageName = "package" + i;
            methodSpec.addStatement("$T $L = $S", String.class, packageName, exported);

            setOfArguments.append(packageName);
            if (i < exports.length - 1) setOfArguments.append(", ");
        }

        methodSpec
                .addStatement("exports = $T.of($L)", Set.class, setOfArguments.toString())
                .endControlFlow()
                .addStatement("return exports");

        methodSpec.returns(stringSetClassName);

        typeSpec.addMethod(methodSpec.build());
    }

    private void generateToString(
            ModuleRepresentation representation,
            TypeSpec.Builder typeSpec,
            String descriptorName
    ) {

        typeSpec.addMethod(
                MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement(
                                "return new $T()" +
                                ".append($S)" +
                                ".append(\"[id=\")" +
                                ".append($S)" +
                                ".append(\", version=\")" +
                                ".append(version())" +
                                ".append(\", apiVersion=\")" +
                                ".append(apiVersion())" +
                                ".append(\", dependencies=\")" +
                                ".append(getVersionedDependencies())" +
                                ".append(\", exports=\")" +
                                ".append(exports())" +
                                ".append(\"]\")" +
                                ".toString()",
                                StringBuilder.class,
                                descriptorName,
                                representation.id()
                        )
                        .returns(String.class)
                        .build()
        );
    }

    private void generateDescriptor(ModuleRepresentation representation) {

        String packageName = representation.model().packageName();
        String simpleName = representation.model().simpleName();
        String descriptorName = simpleName + DESCRIPTOR_SUFFIX;

        TypeSpec.Builder typeSpec = TypeSpec.classBuilder(descriptorName)
                .addSuperinterface(ModuleDescriptor.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        typeSpec.addField(
                FieldSpec.builder(SemVersion.class, "version")
                        .addModifiers(Modifier.PRIVATE)
                        .initializer("null")
                        .build()
        );
        typeSpec.addField(
                FieldSpec.builder(VersionRange.class, "apiVersion")
                        .addModifiers(Modifier.PRIVATE)
                        .initializer("null")
                        .build()
        );
        typeSpec.addMethod(
                MethodSpec.methodBuilder("id")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return $S", representation.id())
                        .returns(String.class)
                        .build()
        );
        typeSpec.addMethod(
                MethodSpec.methodBuilder("version")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .beginControlFlow("if (version == null)")
                        .addStatement("version = $T.parse($S)", SemVersionParser.class, representation.version())
                        .endControlFlow()
                        .addStatement("return version")
                        .returns(SemVersion.class)
                        .build()
        );
        typeSpec.addMethod(
                MethodSpec.methodBuilder("apiVersion")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .beginControlFlow("if (apiVersion == null)")
                        .addStatement("apiVersion = $T.parse($S)", VersionRangeParser.class, representation.apiVersion())
                        .endControlFlow()
                        .addStatement("return apiVersion")
                        .returns(VersionRange.class)
                        .build()
        );
        generateDependencies(representation, typeSpec);
        generateExports(representation, typeSpec);
        generateToString(representation, typeSpec, descriptorName);

        JavaFile javaFile = JavaFile.builder(packageName, typeSpec.build())
                .build();

        try {

            javaFile.writeTo(filer());

        } catch (Exception ex) {
            context().logger().message(MessageType.ERROR, "Failed to create " + descriptorName + " class: " + ex);

        }

    }

    private void generateManifest(ModuleRepresentation representation) {

        String path = ModuleDescriptor.DESCRIPTOR_FILE;

        try (FileWriter writer = context().fileFactory().createWriter(path)) {
            writer.open();
            writer.write(representation.model().qualifiedName() + DESCRIPTOR_SUFFIX);

        } catch (Exception ex) {
            throw new RuntimeException("failed to generate " + path + ".", ex);

        }

    }

}
