package me.bottdev.meshdi.processor;

import java.util.List;

sealed interface DependencyResolutionResult permits
        DependencyResolutionResult.Ok,
        DependencyResolutionResult.Error
{

    record Ok(
        List<ComponentRepresentation> ordered
    ) implements DependencyResolutionResult {}

    record Error(
        Exception exception
    ) implements DependencyResolutionResult {}

    static DependencyResolutionResult ok(List<ComponentRepresentation> ordered) {
        return new Ok(ordered);
    }

    static Error error(Exception exception) {
        return new Error(exception);
    }

}
