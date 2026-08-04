package me.bottdev.meshdi.processor;

import lombok.Getter;
import me.bottdev.kern.meta.core.models.executable.ConstructorModel;
import me.bottdev.kern.meta.core.models.type.ClassModel;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;
import me.bottdev.meshdi.api.annotations.Inject;

import java.util.Collections;
import java.util.List;

@Getter
class ComponentData  {

    private final ClassModel model;
    private final String className;
    private final String qualifier;
    private final InitializationStrategy initializationStrategy;
    private final ScopeType scopeType;
    private final List<ConstructorModel> constructors;
    private final List<ConstructorModel> injectConstructors;

    public ComponentData(
            ClassModel model,
            String className,
            String qualifier,
            InitializationStrategy initializationStrategy,
            ScopeType scopeType,
            List<ConstructorModel> constructors
    ) {
        this.model = model;
        this.className = className;
        this.qualifier = qualifier;
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

    public String getKey() {
        return qualifier.isBlank() ? className : className + "@" + qualifier;
    }

}