package me.bottdev.meshdi.core.reflection;

import lombok.Getter;
import me.bottdev.kern.commons.key.SimpleTypedKey;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.DependencyRequest;
import me.bottdev.kern.dependency.simple.SimpleDependencyRequest;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.api.annotations.Inject;
import me.bottdev.meshdi.api.exceptions.BeanConstructorException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class AbstractBeanInstantiator<T> implements BeanInstantiator<T> {

    @Getter
    protected final Constructor<T> constructor;
    protected final List<DependencyRequest<TypedKey<?>>> dependencies;

    public AbstractBeanInstantiator(Class<? extends T> implementation) throws BeanConstructorException {
        this.constructor = findConstructor(implementation);
        this.dependencies = fetchDependenciesFromConstructor();
    }

    @SuppressWarnings("unchecked")
    private Constructor<T> findConstructor(Class<? extends T> implementation) throws BeanConstructorException {
        Constructor<?>[] constructors = implementation.getDeclaredConstructors();

        if (constructors.length == 1) {
            Constructor<T> ctor = (Constructor<T>) constructors[0];
            ctor.setAccessible(true);
            return ctor;
        } else {
            List<Constructor<?>> injected = Arrays.stream(constructors)
                    .filter(c -> c.isAnnotationPresent(Inject.class))
                    .toList();

            return switch (injected.size()) {
                case 0 -> throw new BeanConstructorException(
                        implementation + " has multiple constructors without @Inject. " +
                                "Annotate exactly one constructor with @Inject."
                );
                case 1 -> {
                    Constructor<T> ctor = (Constructor<T>) injected.getFirst();
                    ctor.setAccessible(true);
                    yield ctor;
                }
                default -> throw new BeanConstructorException(
                        implementation + " has multiple constructors annotated with @Inject. " +
                                "Exactly one constructor must carry @Inject."
                );
            };
        }
    }

    private DependencyRequest<TypedKey<?>> getParameterDependencyRequest(Parameter parameter) {
        Class<?> type = parameter.getType();
        if (!parameter.isAnnotationPresent(Dependency.class))
            return new SimpleDependencyRequest<>(SimpleTypedKey.of(type), DependencyLink.REQUIRED, DependOrder.AFTER);

        Dependency dependency = parameter.getAnnotation(Dependency.class);
        String qualifier = dependency.qualifier();
        DependencyLink link = dependency.link();
        DependOrder order = dependency.order();
        TypedKey<?> key = qualifier == null || qualifier.isEmpty() ?
                SimpleTypedKey.of(type) :
                SimpleTypedKey.of(type, qualifier);

        return new SimpleDependencyRequest<>(key, link, order);
    }

    private List<DependencyRequest<TypedKey<?>>> fetchDependenciesFromConstructor() {
        return Arrays.stream(constructor.getParameters())
                .map(this::getParameterDependencyRequest)
                .toList();
    }

    @Override
    public List<DependencyRequest<TypedKey<?>>> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }
}
