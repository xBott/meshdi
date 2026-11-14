package me.bottdev.meshdi.moduleit.core;

import me.bottdev.meshdi.moduleit.api.ModuleExportRegistry;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleExportConflictException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Thread-safe [ModuleExportRegistry].
///
/// Lookups happen on every class load of every module and never lock. Registration and
/// unregistration are serialised, so a package always has at most one exporting module.
public class SimpleModuleExportRegistry implements ModuleExportRegistry {

    private record Entry(Set<String> packages, ClassLoader classLoader) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, String> idByPackages = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    @Override
    public boolean isRegistered(String id) {
        return entries.containsKey(id);
    }

    /// @throws ModuleExportConflictException if one of the packages is already exported by another module.
    /// Nothing is registered in that case.
    @Override
    public void register(String id, Set<String> packages, ClassLoader classLoader) {
        synchronized (writeLock) {
            if (isRegistered(id)) return;

            for (String packageName : packages) {
                String owner = idByPackages.get(packageName);
                if (owner != null) throw new ModuleExportConflictException(id, packageName, owner);
            }

            Set<String> exported = Set.copyOf(packages);
            entries.put(id, new Entry(exported, classLoader));
            exported.forEach(packageName -> idByPackages.put(packageName, id));
        }
    }

    @Override
    public void unregister(String id) {
        synchronized (writeLock) {
            Entry entry = entries.remove(id);
            if (entry == null) return;
            // Remove only mappings this module owns.
            entry.packages().forEach(packageName -> idByPackages.remove(packageName, id));
        }
    }

    @Override
    public Set<String> getPackagesById(String id) {
        Entry entry = entries.get(id);
        return entry == null ? Set.of() : entry.packages();
    }

    @Override
    public ClassLoader getClassLoaderById(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.classLoader();
    }

    @Override
    public String getIdByPackage(String packageName) {
        return idByPackages.get(packageName);
    }

}
