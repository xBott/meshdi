package me.bottdev.meshdi.processor;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import me.bottdev.kern.dependency.DependencyAware;
import me.bottdev.kern.dependency.DependencyRequest;
import me.bottdev.kern.meta.core.models.type.ClassModel;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;

import java.util.Collections;
import java.util.List;

@Getter
class ComponentRepresentation implements DependencyAware<String> {

    private final ClassModel model;
    private final String className;
    private final String qualifier;
    private final InitializationStrategy initializationStrategy;
    private final ScopeType scopeType;
    private final List<DependencyRequest<String>> dependencies;

    @Builder
    public ComponentRepresentation(
            ClassModel model,
            String className,
            String qualifier,
            InitializationStrategy initializationStrategy,
            ScopeType scopeType,
            @Singular List<DependencyRequest<String>> dependencies
    ) {
        this.model = model;
        this.className = className;
        this.qualifier = qualifier;
        this.initializationStrategy = initializationStrategy;
        this.scopeType = scopeType;
        this.dependencies = Collections.unmodifiableList(dependencies);
    }

    public boolean hasQualifier() {
        return !qualifier.isBlank();
    }

    @Override
    public String dependencyKey() {
        return hasQualifier() ? className + "@" + qualifier : className;
    }

}