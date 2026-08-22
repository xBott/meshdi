package me.bottdev.meshdi.moduleit.api.library;

/// Determines which class loader is used for library.
public enum LibraryScope {
    /// Library is loaded from a separate independent class loader.
    ISOLATED,
    /// Library is loaded from a common class loader for all modules.
    SHARED
}
