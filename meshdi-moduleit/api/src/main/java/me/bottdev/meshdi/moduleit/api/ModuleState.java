package me.bottdev.meshdi.moduleit.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// Represents a state of module.
@RequiredArgsConstructor
public enum ModuleState {
    /// Module is resolved.
    RESOLVED(false),
    /// Libraries of the module are loaded successfully.
    READY(false),
    /// Module is successfully loaded and class loader is opened.
    LOADED(false),

    /// Module is starting. Transition from **LOADED**, **STOPPED**, **START_FAILED** or **UNLOAD_FAILED**.
    STARTING(true),
    /// Module is successfully started. Transition from **STARTING**.
    STARTED(false),
    /// An error occurred while starting the module. Transition from **STARTING**.
    /// The start was rolled back, so the module can be started again or unloaded.
    START_FAILED(false),

    /// Not used: a restart is a stop followed by a start, and a module goes through the states of both.
    /// @deprecated never assigned by the module manager; kept for source compatibility.
    @Deprecated
    RESTARTING(true),
    /// Not used: a failed restart ends in **STOP_FAILED** or **START_FAILED**, which say which half failed.
    /// @deprecated never assigned by the module manager; kept for source compatibility.
    @Deprecated
    RESTART_FAILED(false),

    /// Module is stopping. Transition from **STARTED** or **FAILED**.
    STOPPING(true),
    /// An error occurred while stopping the module. Transition from **STOPPING**.
    /// The context is kept, so the stop can be retried.
    STOP_FAILED(false),
    /// Module is stopped. Transition from **STOPPING**.
    STOPPED(false),

    /// Module is unloading. Transition from **STOPPED** or **LOADED**.
    UNLOADING(true),
    /// An error occurred while unloading the module, typically because other modules still depend on it.
    /// Transition from **UNLOADING**. Nothing was released, so the module can be started or unloaded again.
    UNLOAD_FAILED(false);

    @Getter
    private final boolean intermediate;

}
