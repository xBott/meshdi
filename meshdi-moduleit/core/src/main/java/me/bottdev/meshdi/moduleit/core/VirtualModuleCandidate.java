package me.bottdev.meshdi.moduleit.core;

import lombok.RequiredArgsConstructor;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;

import java.net.URL;

@RequiredArgsConstructor
class VirtualModuleCandidate implements InternalModuleCandidate {

    private final VirtualModuleHandle handle;

    @Override
    public InternalModuleHandle createHandle() {
        return handle;
    }

    @Override
    public String sourceKey() {
        return "virtual:" + handle.descriptor().id();
    }

    @Override
    public ModuleDescriptor descriptor() throws ModuleDescriptorException {
        return handle.descriptor();
    }

    @Override
    public URL sourceUrl() {
        return null;
    }
}
