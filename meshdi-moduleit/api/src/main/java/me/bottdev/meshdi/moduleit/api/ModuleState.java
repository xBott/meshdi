package me.bottdev.meshdi.moduleit.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// Represents a state of module.
@RequiredArgsConstructor
public enum ModuleState {
    /// Module is resolved and class loader is opened.
    LOADED(false),
    /// Module is starting. Transition from **LOADED** or **STOPPED**.
    STARTING(true),
    /// Module is successfully started. Transition from **STARTING** or **RESTARTING**.
    STARTED(false),
    /// An error occurred while starting the module. Transition from **STARTING**, **RESTARTING** or **STOPPING**.
    FAILED(false),
    /// Module is restarting. Transition from **LOADED** or **STARTED**.
    RESTARTING(true),
    /// Module is stopping. Transition from **STARTED** or **FAILED**.
    STOPPING(true),
    /// Module is stopped. Transition from **STOPPING**.
    STOPPED(false);

    @Getter
    private final boolean intermediate;

}
