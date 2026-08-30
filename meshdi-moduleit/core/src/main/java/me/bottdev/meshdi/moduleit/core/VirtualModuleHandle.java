package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.kern.version.SemVersion;
import me.bottdev.kern.version.VersionRange;
import me.bottdev.kern.version.VersionRangeParser;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleState;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class VirtualModuleHandle implements ModuleHandle {

    private final Context context;
    private final ModuleDescriptor descriptor;

    public VirtualModuleHandle(
            @NonNull String id,
            @NonNull SemVersion version,
            @NonNull Context context
    ) {
        this.context = context;
        this.descriptor = createVirtualDescriptor(id, version);
    }

    private ModuleDescriptor createVirtualDescriptor(String id, SemVersion version) {
        return new ModuleDescriptor() {
            @Override
            public String id() { return id; }

            @Override
            public SemVersion version() { return version; }

            @Override
            public VersionRange apiVersion() { return VersionRangeParser.parse("*"); }

            @Override
            public Set<String> exports() { return Set.of(); }

            @Override
            public Set<RepositoryDeclaration> repositories() { return Set.of(); }

            @Override
            public Set<LibraryRequirement> libraries() { return Set.of(); }

            @Override
            public List<VersionedDependencyRequest<String>> getVersionedDependencies() { return List.of(); }
        };
    }

    @Override
    public ModuleState state() {
        // Виртуальный модуль всегда запущен
        return ModuleState.STARTED;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptor;
    }
    
    @Override
    public Context context() {
        return context;
    }

    @Override
    public ClassLoader classLoader() {
        return context != null ? context.getClass().getClassLoader() : ClassLoader.getSystemClassLoader();
    }

    @Override
    public List<Path> libraries() {
        return List.of();
    }
}
