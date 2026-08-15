package me.bottdev.meshdi.moduleit.api;

import java.util.Set;

/// Registry which stores package names associated with id of module they belong to.
/// Used in class loading for loading classes from other modules.
public interface ModuleExportRegistry {

    boolean isRegistered(String id);

    void register(String id, Set<String> packages, ClassLoader classLoader);

    void unregister(String id);

    Set<String> getPackagesById(String id);

    ClassLoader getClassLoaderById(String id);

    String getIdByPackage(String packageName);

    default String getIdByClassName(String className) {
        int lastDot = className.lastIndexOf(".");
        String packageName = lastDot < 0 ? "" : className.substring(0, lastDot);
        return getIdByPackage(packageName);
    }


}
