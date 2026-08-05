package me.bottdev.meshdi.processor;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import me.bottdev.kern.dependency.DependencyAware;
import me.bottdev.kern.dependency.DependencyRequest;
import me.bottdev.kern.meta.core.models.executable.MethodModel;
import me.bottdev.kern.meta.core.models.type.ClassModel;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
class ComponentRepresentation implements DependencyAware<String> {

    private final ClassModel model;
    private final String className;
    private final String qualifier;
    private final InitializationStrategy initializationStrategy;
    private final ScopeType scopeType;
    private final List<DependencyRequest<String>> dependencies;
    private final Map<BeanLifecycleEventType, List<MethodModel>> eventMethods;

    @Builder
    public ComponentRepresentation(
            ClassModel model,
            String className,
            String qualifier,
            InitializationStrategy initializationStrategy,
            ScopeType scopeType,
            @Singular List<DependencyRequest<String>> dependencies,
            Map<BeanLifecycleEventType, List<MethodModel>> eventMethods
    ) {
        this.model = model;
        this.className = className;
        this.qualifier = qualifier;
        this.initializationStrategy = initializationStrategy;
        this.scopeType = scopeType;
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.eventMethods = Map.copyOf(eventMethods);
    }

    public boolean hasQualifier() {
        return !qualifier.isBlank();
    }

    public List<MethodModel> getEventMethods(BeanLifecycleEventType type) {
        return eventMethods.getOrDefault(type, Collections.emptyList());
    }

    public boolean hasEventMethods(BeanLifecycleEventType type) {
        if (!eventMethods.containsKey(type)) return false;
        return !eventMethods.get(type).isEmpty();
    }

    @Override
    public String dependencyKey() {
        return hasQualifier() ? className + "@" + qualifier : className;
    }

}
