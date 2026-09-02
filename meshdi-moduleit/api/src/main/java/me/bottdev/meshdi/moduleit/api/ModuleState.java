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

    /// Module is starting. Transition from **LOADED** or **STOPPED**.
    STARTING(true),
    /// Module is successfully started. Transition from **STARTING** or **RESTARTING**.
    STARTED(false),
    /// An cause occurred while starting the module. Transition from **STARTING**.
    START_FAILED(false),

    /// Module is restarting. Transition from **LOADED** or **STARTED**.
    RESTARTING(true),
    /// An cause occurred while restarting the module. Transition from **RESTARTING**.
    RESTART_FAILED(false),

    /// Module is stopping. Transition from **STARTED** or **FAILED**.
    STOPPING(true),
    /// An cause occurred while stopping the module. Transition from **STOPPING**.
    STOP_FAILED(false),
    /// Module is stopped. Transition from **STOPPING**.
    STOPPED(false),

    /// Module is unloading. Transition from **STOPPED** or **LOADED**.
    UNLOADING(true),
    /// An cause occurred while unloading the module. Transition from **UNLOADING**.
    UNLOAD_FAILED(false);

    @Getter
    private final boolean intermediate;

}
