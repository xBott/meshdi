package me.bottdev.meshdi.processor;

import com.google.auto.service.AutoService;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
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
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.api.annotations.Inject;

import javax.annotation.processing.Processor;
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
        private final List<ConstructorModel> constructors;
        private final List<ConstructorModel> injectConstructors;

        public ComponentData(
                ClassModel model,
                String componentKey,
                List<ConstructorModel> constructors
        ) {
            this.model = model;
            this.componentKey = componentKey;
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

        private final String dependencyKey;
        private final List<DependencyRequest<String>> dependencies;

        @Builder
        public ComponentDefinition(
                String dependencyKey,
                @Singular
                List<DependencyRequest<String>> dependencies
        ) {
            this.dependencyKey = Objects.requireNonNull(dependencyKey, "Component qualified name must be non-null");
            this.dependencies = Collections.unmodifiableList(dependencies);
        }

        @Override
        public String dependencyKey() {
            return dependencyKey;
        }

        @Override
        public List<DependencyRequest<String>> getDependencies() {
            return dependencies;
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
                    String qualifier = component.qualifier();
                    String componentKey = qualifier.isBlank() ? className : className + "@" + qualifier;

                    return new ComponentData(model, componentKey, model.constructors());

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

                    String componentKey = data.getComponentKey();

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

                    return new ComponentDefinition(componentKey, dependencies);

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

                                return ok();

                            } catch (DependencyException | CircularDependencyException ex) {
                                return error(ex.getMessage());

                            }

                        })
                )
                .finish();

    }

}
