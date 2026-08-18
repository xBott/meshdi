package me.bottdev.meshdi.moduleit.api;

import lombok.NonNull;

/// Represents a record with information whether the module was successfully unloaded from
/// JVM's heap or leaked.
public sealed interface ModuleUnloadGCReport permits
        ModuleUnloadGCReport.Success,
        ModuleUnloadGCReport.NotUnloaded,
        ModuleUnloadGCReport.Leaked
{

    static ModuleUnloadGCReport success(
            @NonNull String id
    ) {
        return new Success(id);
    }

    static ModuleUnloadGCReport notUnloaded(
            @NonNull String id
    ) {
        return new NotUnloaded(id);
    }

    static ModuleUnloadGCReport leaked(
            @NonNull String id,
            @NonNull Throwable error
    ) {
        return new Leaked(id, error);
    }

    record Success(@NonNull String id) implements ModuleUnloadGCReport {}

    record NotUnloaded(@NonNull String id) implements ModuleUnloadGCReport {}

    record Leaked(@NonNull String id, @NonNull Throwable error) implements ModuleUnloadGCReport {}

}
