package me.bottdev.meshdi.moduleit.api;

import me.bottdev.meshdi.api.Context;

/// Represents loaded module. Created and managed by [ModuleManager].
public interface ModuleHandle {

    /// @return  Current state of the handle.
    ModuleState state();

    /// @return Descriptor of the module.
    ModuleDescriptor descriptor();

    /// @return Context of the module if context is started. Otherwise, returns null.
    Context context();

    /// @return class loader that loads classes of the module.
    ClassLoader classLoader();

}
