package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import lombok.Setter;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleState;

public class SimpleModuleHandle implements ModuleHandle {

    private final ModuleCandidate candidate;
    private final ClassLoader classLoader;
    @Setter
    private ModuleState state = ModuleState.LOADED;
    @Setter
    private Context context = null;

    public SimpleModuleHandle(
            @NonNull ModuleCandidate candidate,
            @NonNull ClassLoader classLoader
    ) {
        this.candidate = candidate;
        this.classLoader = classLoader;
    }

    @Override
    public ModuleState state() {
        return state;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return candidate.descriptor();
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public ClassLoader classLoader() {
        return classLoader;
    }
}
