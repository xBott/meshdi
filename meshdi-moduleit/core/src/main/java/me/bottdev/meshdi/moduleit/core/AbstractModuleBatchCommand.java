package me.bottdev.meshdi.moduleit.core;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.ModuleBatchCommand;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;

import java.util.Collections;
import java.util.List;

public abstract class AbstractModuleBatchCommand<R> implements ModuleBatchCommand<R> {

    protected final List<ModuleHandle> handles;

    public AbstractModuleBatchCommand(
            @NonNull List<ModuleHandle> handles
    ) {
        this.handles = handles;
    }

    @Override
    public List<ModuleHandle> handles() {
        return Collections.unmodifiableList(handles);
    }

}
