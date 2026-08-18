package me.bottdev.meshdi.moduleit.core;

import lombok.Builder;
import lombok.NonNull;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.meshdi.moduleit.api.ModuleExportRegistry;
import me.bottdev.meshdi.moduleit.api.ModuleLoadEnvironment;

import java.util.Set;

@Builder
public record SimpleModuleLoadEnvironment(
        @NonNull SemVersion apiVersion,
        @NonNull ClassLoader apiLoader,
        @NonNull Set<String> apiPackages,
        @NonNull ModuleExportRegistry exportRegistry
) implements ModuleLoadEnvironment {}
