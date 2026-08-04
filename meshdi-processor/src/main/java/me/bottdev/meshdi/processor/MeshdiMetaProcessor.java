package me.bottdev.meshdi.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import me.bottdev.kern.commons.key.KeyUtils;
import me.bottdev.kern.dependency.*;
import me.bottdev.kern.dependency.containers.SimpleDependentContainer;
import me.bottdev.kern.dependency.graph.GraphDependencyResolver;
import me.bottdev.kern.meta.apt.AbstractMetaProcessor;
import me.bottdev.kern.meta.core.FileWriter;
import me.bottdev.kern.meta.core.MessageType;
import me.bottdev.kern.meta.core.configuration.ProcessorConfigurationBuilder;
import me.bottdev.kern.meta.core.models.ModelKind;
import me.bottdev.kern.meta.core.models.executable.ConstructorModel;
import me.bottdev.kern.struct.algorithms.cycle.SimpleCycleDetector;
import me.bottdev.kern.struct.algorithms.sort.KahnSorter;
import me.bottdev.meshdi.api.*;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.core.bindings.FactoryBinding;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static me.bottdev.kern.meta.core.diagnostic.DiagnosticRuleResult.error;
import static me.bottdev.kern.meta.core.diagnostic.DiagnosticRuleResult.ok;

@AutoService(Processor.class)
public class MeshdiMetaProcessor extends AbstractMetaProcessor {

    private static final DependencyResolver dependencyResolver = new GraphDependencyResolver(new KahnSorter(new SimpleCycleDetector()));

    private final Set<String> discoveredComponents = new HashSet<>();
    private final Map<String, ComponentRepresentation> representations = new HashMap<>();

    @Override
    protected void configure(ProcessorConfigurationBuilder builder) {

        builder.select(ModelKind.CLASS, Component.class)
                .peek(model -> context().logger().message(MessageType.INFO, "Found DI component: " + model.qualifiedName()))
                .map(model -> {

                    String className = model.qualifiedName();
                    Component component = model.annotation(Component.class).orElseThrow();
                    InitializationStrategy initializationStrategy = component.init();
                    ScopeType scopeType = component.scope();
                    String qualifier = component.qualifier();

                    return new ComponentData(
                            model,
                            className,
                            qualifier,
                            initializationStrategy,
                            scopeType,
                            model.constructors()
                    );

                })
                .validate(rules -> rules
                        .rule(data -> {
                            if (data.getConstructors().size() == 1) return ok();
                            if (!data.hasInjectConstructors())
                                return error(data.getKey() + " has multiple constructors without @Inject. Annotate exactly one constructor with @Inject.");
                            if (data.getInjectConstructors().size() > 1)
                                return error(data.getKey() + " has multiple constructors annotated with @Inject. Exactly one constructor must carry @Inject.");
                            return ok();
                        })
                        .rule(data -> discoveredComponents.add(data.getKey()) ? ok() : error(data.getKey() + " found duplicate component key"))
                )
                .map(data -> {

                    ConstructorModel constructor = data.getCorrectConstructor();
                    List<DependencyRequest<String>> dependencies = constructor.parameters().stream()
                            .map(parameterModel -> {

                                String typeName = parameterModel.type().qualifiedName();
                                Optional<Dependency> dependencyOptional = parameterModel.annotation(Dependency.class);

                                if (dependencyOptional.isPresent()) {
                                    Dependency dependency = dependencyOptional.get();
                                    String qualifier = dependency.qualifier();
                                    DependencyLink link = dependency.link();
                                    DependOrder order = dependency.order();
                                    String dependencyKey = qualifier.isBlank() ? typeName : typeName + "@" + qualifier;

                                    return new DependencyRequest<>(
                                            dependencyKey,
                                            link,
                                            order
                                    );

                                } else {
                                    return new DependencyRequest<>(
                                            typeName,
                                            DependencyLink.REQUIRED,
                                            DependOrder.AFTER
                                    );
                                }
                            })
                            .toList();

                    return new ComponentRepresentation(
                            data.getModel(),
                            data.getClassName(),
                            data.getQualifier(),
                            data.getInitializationStrategy(),
                            data.getScopeType(),
                            dependencies
                    );

                })
                .run(representation -> representations.put(representation.dependencyKey(), representation));

        builder.afterAll()
                .map(() -> {

                    SimpleDependentContainer.Builder<String, ComponentRepresentation> containerBuilder =
                            SimpleDependentContainer.builder();
                    representations.forEach((_, representation) -> containerBuilder.add(representation));
                    DependentContainer<String, ComponentRepresentation> container = containerBuilder.build();

                    try {
                        ResolutionResult<ComponentRepresentation> result = dependencyResolver.resolve(container);
                        return DependencyResolutionResult.ok(result.ordered());

                    } catch (Exception ex) {
                        return DependencyResolutionResult.error(ex);

                    }

                })
                .validate(rules -> rules
                        .rule(result -> switch (result) {
                            case DependencyResolutionResult.Ok _ -> ok();
                            case DependencyResolutionResult.Error error -> error(error.exception().getMessage());
                        })
                )
                .map(result -> (DependencyResolutionResult.Ok) result)
                .peek(ok -> {
                    context().logger().message(MessageType.INFO, "Successfully resolved components! Component order:");
                    ok.ordered().forEach(definition -> context().logger().message(MessageType.INFO, " - " + definition.dependencyKey()));
                })
                .generate(ok -> {
                    List<ComponentRepresentation> ordered = ok.ordered();
                    generateDefinitions(ordered);
                    generateServiceFile(ordered);
                });

    }

    private MethodSpec generateCreateMethod(
            ComponentRepresentation representation,
            ClassName componentClassName
    ) {

        List<DependencyRequest<String>> dependencies = representation.getDependencies();

        ParameterizedTypeName returnType = ParameterizedTypeName.get(
                ClassName.get(BindingBuilder.class),
                componentClassName
        );

        MethodSpec.Builder builder = MethodSpec.methodBuilder("create")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType);

        if (representation.hasQualifier()) {
            builder.addStatement("$T<$T> builder = $T.builder($T.key($T.class, $S))",
                    FactoryBinding.Builder.class,
                    componentClassName,
                    FactoryBinding.class,
                    KeyUtils.class,
                    componentClassName,
                    representation.getQualifier()
            );

        } else {
            builder.addStatement("$T<$T> builder = $T.builder($T.key($T.class))",
                    FactoryBinding.Builder.class,
                    componentClassName,
                    FactoryBinding.class,
                    KeyUtils.class,
                    componentClassName
            );

        }


        dependencies.forEach(dependency -> {
            String dependencyKey = dependency.key();
            context().logger().message(MessageType.INFO, dependencyKey);
            ComponentRepresentation dependencyRepresentation = representations.get(dependencyKey);
            ClassName dependencyClassName = ClassName.bestGuess(dependencyRepresentation.getModel().qualifiedName());

            if (dependencyRepresentation.hasQualifier()) {
                String qualifier = dependencyRepresentation.getQualifier();
                builder.addStatement(
                        "builder.dependsOn($T.key($T.class, $S), $T.$L, $T.$L)",
                        KeyUtils.class,
                        dependencyClassName,
                        qualifier,
                        DependencyLink.class,
                        dependency.link(),
                        DependOrder.class,
                        dependency.order()
                );
            } else {
                builder.addStatement(
                        "builder.dependsOn($T.key($T.class), $T.$L, $T.$L)",
                        KeyUtils.class,
                        dependencyClassName,
                        DependencyLink.class,
                        dependency.link(),
                        DependOrder.class,
                        dependency.order()
                );
            }
        });


        builder.beginControlFlow("builder.factory(resolver ->");

        for (int i = 0; i < dependencies.size(); i++) {
            DependencyRequest<String> dependency = dependencies.get(i);
            String dependencyKey = dependency.key();
            context().logger().message(MessageType.INFO, dependencyKey);
            ComponentRepresentation dependencyRepresentation = representations.get(dependencyKey);
            ClassName dependencyClassName = ClassName.bestGuess(dependencyRepresentation.getModel().qualifiedName());

            if (dependencyRepresentation.hasQualifier()) {
                String qualifier = dependencyRepresentation.getQualifier();
                builder.addStatement(
                        "$T dependency$L = resolver.get($T.key($T.class), $S)",
                        dependencyClassName,
                        i,
                        KeyUtils.class,
                        dependencyClassName,
                        qualifier
                );
            } else {
                builder.addStatement(
                        "$T dependency$L = resolver.get($T.key($T.class))",
                        dependencyClassName,
                        i,
                        KeyUtils.class,
                        dependencyClassName
                );
            }
        }

        StringBuilder returnStatement = new StringBuilder("return new $T(");
        String args = IntStream.range(0, dependencies.size())
                .mapToObj(i -> "dependency" + i)
                .collect(Collectors.joining(", "));
        returnStatement.append(args).append(")");
        builder.addStatement(returnStatement.toString(), componentClassName);

        builder.endControlFlow(")");

        StringBuilder statement = new StringBuilder("builder");
        switch (representation.getInitializationStrategy()) {
            case LAZY -> statement.append(".lazy()");
            case EAGER -> statement.append(".eager()");
        }
        switch (representation.getScopeType()) {
            case SINGLETON -> statement.append(".singleton()");
            case PROTOTYPE -> statement.append(".prototype()");
        }

        if (statement.length() > "builder".length()) {
            builder.addStatement(statement.toString());
        }

        builder.addStatement("return builder");

        return builder.build();
    }

    private void generateDefinition(ComponentRepresentation representation) {

        String packageName = representation.getModel().packageName();
        String simpleName = representation.getModel().simpleName();
        String definitionName = simpleName + "Definition";

        ClassName componentClassName = ClassName.bestGuess(representation.getModel().qualifiedName());

        ParameterizedTypeName superName = ParameterizedTypeName.get(
                ClassName.get(ComponentDefinition.class),
                componentClassName
        );

        TypeSpec typeSpec = TypeSpec.classBuilder(definitionName)
                .addSuperinterface(superName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(generateCreateMethod(representation, componentClassName))
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
                .build();

        try {

            javaFile.writeTo(filer());

        } catch (Exception ex) {
            context().logger().message(MessageType.ERROR, "Failed to create " + definitionName + " class: " + ex);

        }

    }

    private void generateDefinitions(List<ComponentRepresentation> representations) {
        representations.forEach(representation -> {
            context().logger().message(MessageType.INFO, "Generating " + representation.dependencyKey());
            generateDefinition(representation);
        });
    }

    private void generateServiceFile(List<ComponentRepresentation> representations) {
        String path = "META-INF/services/me.bottdev.meshdi.api.ComponentDefinition";
        try (FileWriter writer = context().fileFactory().createWriter(path)) {

            writer.open();

            for (ComponentRepresentation representation : representations) {
                String qualifiedName = representation.getModel().qualifiedName() + "Definition";
                writer.write(qualifiedName + "\n");

            }

        } catch (Exception ex) {
            context().logger().message(MessageType.ERROR, "Failed to create " + path + " file: " + ex);

        }
    }

}
