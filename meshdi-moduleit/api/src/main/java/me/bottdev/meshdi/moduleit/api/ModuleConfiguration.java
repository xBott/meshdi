package me.bottdev.meshdi.moduleit.api;

/// Defines the module's capabilities: extension points, lifecycle listeners.
public interface ModuleConfiguration {

    default void onStart() {};

    default void onStop() {};

}
