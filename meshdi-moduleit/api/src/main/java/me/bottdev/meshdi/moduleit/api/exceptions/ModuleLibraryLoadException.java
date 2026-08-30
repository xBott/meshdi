package me.bottdev.meshdi.moduleit.api.exceptions;

import lombok.Getter;
import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.meshdi.moduleit.api.diagnostic.LibraryLoadDiagnostic;

public class ModuleLibraryLoadException extends ModulePrepareException {

    @Getter
    private final Diagnostics<LibraryLoadDiagnostic> diagnostics;

    public ModuleLibraryLoadException(String message, Diagnostics<LibraryLoadDiagnostic> diagnostics) {
        super(message);
        this.diagnostics = diagnostics;
    }

}
