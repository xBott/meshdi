package me.bottdev.meshdi.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import me.bottdev.kern.commons.key.KeyUtils;
import me.bottdev.kern.dependency.*;
import me.bottdev.kern.dependency.containers.SimpleDependentContainer;
import me.bottdev.kern.dependency.exceptions.DependencyException;
import me.bottdev.kern.dependency.graph.GraphDependencyResolver;
import me.bottdev.kern.meta.apt.AbstractMetaProcessor;
import me.bottdev.kern.meta.core.MessageType;
import me.bottdev.kern.meta.core.configuration.ProcessorConfigurationBuilder;
import me.bottdev.kern.meta.core.models.ModelKind;
import me.bottdev.kern.meta.core.models.executable.ConstructorModel;
import me.bottdev.kern.meta.core.models.type.ClassModel;
import me.bottdev.kern.struct.algorithms.cycle.SimpleCycleDetector;
import me.bottdev.kern.struct.algorithms.sort.CircularDependencyException;
import me.bottdev.kern.struct.algorithms.sort.KahnSorter;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.api.annotations.Inject;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Modifier;
import java.util.*;

import static me.bottdev.kern.meta.core.diagnostic.DiagnosticRuleResult.error;
import static me.bottdev.kern.meta.core.diagnostic.DiagnosticRuleResult.ok;

@AutoService(Processor.class)
public class MeshdiMetaProcessor extends AbstractMetaProcessor {

    private static final DependencyResolver dependencyResolver = new GraphDependencyResolver(new KahnSorter(new SimpleCycleDetector()));

    @Getter
    private static class ComponentData  {

        private final ClassModel model;
        private final String componentKey;
        private final InitializationStrategy initializationStrategy;
        private final ScopeType scopeType;
        private final List<ConstructorModel> constructors;
        private final List<ConstructorModel> injectConstructors;

        public ComponentData(
                ClassModel model,
                String componentKey,
                InitializationStrategy initializationStrategy,
                ScopeType scopeType,
                List<ConstructorModel> constructors
        ) {
            this.model = model;
            this.componentKey = componentKey;
            this.initializationStrategy = initializationStrategy;
            this.scopeType = scopeType;
            this.constructors = Collections.unmodifiableList(constructors);
            this.injectConstructors = constructors.stream()
                    .filter(constructorModel -> constructorModel.annotation(Inject.class).isPresent())
                    .toList();

        }

        public boolean hasInjectConstructors() {
            return !injectConstructors.isEmpty();
        }

        public ConstructorModel getCorrectConstructor() {

            if (constructors.size() == 1) {
                return constructors.getFirst();

            } else {
                return injectConstructors.getFirst();

            }

        }

    }

    private static class ComponentDefinition implements DependencyAware<String> {

        @Getter
        private final ClassModel model;
        private final String dependencyKey;
        @Getter private final InitializationStrategy initializationStrategy;
        @Getter private final ScopeType scopeType;
        @Getter private final List<DependencyRequest<String>> dependencies;

        @Builder
        public ComponentDefinition(
                ClassModel model,
                String dependencyKey,
                InitializationStrategy initializationStrategy,
                ScopeType scopeType,
                @Singular List<DependencyRequest<String>> dependencies
        ) {
            this.model = model;
            this.dependencyKey = Objects.requireNonNull(dependencyKey, "Component qualified name must be non-null");
            this.initializationStrategy = initializationStrategy;
            this.scopeType = scopeType;
            this.dependencies = Collections.unmodifiableList(dependencies);
        }

        @Override
        public String dependencyKey() {
            return dependencyKey;
        }

    }

    private final Set<String> discoveredComponents = new HashSet<>();
    private final SimpleDependentContainer.Builder<String, ComponentDefinition> containerBuilder =
            SimpleDependentContainer.builder();

    @Override
    protected void configure(ProcessorConfigurationBuilder builder) {

        builder.bound(ModelKind.CLASS, Component.class)
                .peek(model -> context().logger().message(MessageType.INFO, "Found DI component: " + model.qualifiedName()))
                .map(model -> {

                    String className = model.qualifiedName();
                    Component component = model.annotation(Component.class).orElseThrow();
                    InitializationStrategy initializationStrategy = component.init();
                    ScopeType scopeType = component.scope();
                    String qualifier = component.qualifier();
                    String componentKey = qualifier.isBlank() ? className : className + "@" + qualifier;

                    return new ComponentData(
                            model,
                            componentKey,
                            initializationStrategy,
                            scopeType,
                            model.constructors()
                    );

                })
                .validate(rules -> rules
                        .rule(data -> {
                            if (data.getConstructors().size() == 1) return ok();
                            if (!data.hasInjectConstructors())
                                return error(data.getComponentKey() + " has multiple constructors without @Inject. Annotate exactly one constructor with @Inject.");
                            if (data.getInjectConstructors().size() > 1)
                                return error(data.getComponentKey() + " has multiple constructors annotated with @Inject. Exactly one constructor must carry @Inject.");
                            return ok();
                        })
                        .rule(data -> discoveredComponents.add(data.getComponentKey()) ? ok() : error(data.getComponentKey() + " found duplicate component key"))
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
                                    String dependencyKey = typeName + "@" + qualifier;

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

                    return new ComponentDefinition(
                            data.getModel(),
                            data.getComponentKey(),
                            data.getInitializationStrategy(),
                            data.getScopeType(),
                            dependencies
                    );

                })
                .finishWith(containerBuilder::add);

        builder.standalone()
                .validate(rules -> rules
                        .rule(() -> {

                            DependentContainer<String, ComponentDefinition> container = containerBuilder.build();
                            try {
                                ResolutionResult<ComponentDefinition> result = dependencyResolver.resolve(container);

                                context().logger().message(MessageType.INFO, "Successfully resolved components! Component order:");
                                for (int i = 0; i < result.layers().size(); i++) {
                                    List<ComponentDefinition> layer = result.layers().get(i);
                                    context().logger().message(MessageType.INFO, " - Layer #" + i + ":");
                                    layer.forEach(definition -> context().logger().message(MessageType.INFO, "    - " + definition.dependencyKey()));
                                }

                                createBoostrap(result);

                                return ok();

                            } catch (DependencyException | CircularDependencyException ex) {
                                return error(ex.getMessage());

                            }

                        })
                )
                .finish();

    }

    private void createBoostrap(ResolutionResult<ComponentDefinition> definitions) {

        context().logger().message(MessageType.INFO, definitions.ordered().size() + " ");
        String packageName = definitions.ordered().getFirst().getModel().packageName() + ".generated";

        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder("bootstrap")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ContextMesh.class, "mesh")
                .addStatement("$T builder = new $T()", SimpleContextBuilder.class, SimpleContextBuilder.class);

        definitions.ordered().forEach(definition -> {
            ClassName className = ClassName.bestGuess(definition.model.qualifiedName());
            StringBuilder statement = new StringBuilder();
            statement.append("builder.construct($T.key($T.class), config -> config");
            switch (definition.getInitializationStrategy()) {
                case LAZY -> statement.append(".lazy()");
                case EAGER -> statement.append(".eager()");
            }
            switch (definition.getScopeType()) {
                case SINGLETON -> statement.append(".singleton()");
                case PROTOTYPE -> statement.append(".prototype()");
            }
            statement.append(")");
            methodSpec.addStatement(statement.toString(), KeyUtils.class, className);
        });

        TypeSpec typeSpec = TypeSpec.classBuilder("ContextBoostrapImpl")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(methodSpec.build())
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
                .build();

        try {
            javaFile.writeTo(filer());

        } catch (Exception ex) {
            context().logger().message(MessageType.ERROR, "Failed to create ContextBootstrapImpl class.");

        }

    }

}
