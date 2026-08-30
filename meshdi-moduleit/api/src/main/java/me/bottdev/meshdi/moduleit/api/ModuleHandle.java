package me.bottdev.meshdi.moduleit.api;

import java.nio.file.Path;
import java.util.List;
import me.bottdev.meshdi.api.Context;

/// Represents loaded module. Created and managed by [ModuleManager].
public interface ModuleHandle {

    /// @return Candidate from which this module was loaded.
    ModuleCandidate candidate();

    /// @return  Current state of the handle.
    ModuleState state();

    default boolean isPersistent() {
        return false;
    }

    /// @return Descriptor of the module.
    ModuleDescriptor descriptor();

    /// @return Context of the module if context is started. Otherwise, returns null.
    Context context();

    /// @return class loader that loads classes of the module.
    ClassLoader classLoader();

    /// @return paths to downloaded isolated library JARs.
    List<Path> libraries();

}
