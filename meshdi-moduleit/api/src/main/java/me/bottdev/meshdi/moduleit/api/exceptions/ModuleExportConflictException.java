package me.bottdev.meshdi.moduleit.api.exceptions;

import lombok.Getter;

/// Thrown when a module tries to export a package that another module already exports.
@Getter
public class ModuleExportConflictException extends ModuleRuntimeException {

    private final String moduleId;
    private final String packageName;
    private final String ownerId;

    public ModuleExportConflictException(String moduleId, String packageName, String ownerId) {
        super("Module \"" + moduleId + "\" cannot export package " + packageName
                + ": it is already exported by module \"" + ownerId + "\".");
        this.moduleId = moduleId;
        this.packageName = packageName;
        this.ownerId = ownerId;
    }

}
