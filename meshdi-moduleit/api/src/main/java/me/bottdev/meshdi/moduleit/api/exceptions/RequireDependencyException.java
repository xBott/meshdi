package me.bottdev.meshdi.moduleit.api.exceptions;

import lombok.Getter;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;

import java.util.List;

public class RequireDependencyException extends Exception {

    @Getter
    private final List<ModuleHandle> dependencyHandles;

    public RequireDependencyException(String message, List<ModuleHandle> dependencyHandles) {
        super(message);
        this.dependencyHandles = dependencyHandles;
    }

    public RequireDependencyException(String message, Throwable cause, List<ModuleHandle> dependencyHandles) {
        super(message, cause);
        this.dependencyHandles = dependencyHandles;
    }

}
