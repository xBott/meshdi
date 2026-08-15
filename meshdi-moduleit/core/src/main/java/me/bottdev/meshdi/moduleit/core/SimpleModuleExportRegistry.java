package me.bottdev.meshdi.moduleit.core;

import me.bottdev.meshdi.moduleit.api.ModuleExportRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SimpleModuleExportRegistry implements ModuleExportRegistry {

    private record Entry(Set<String> packages, ClassLoader classLoader) {}

    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, String> idByPackages = new HashMap<>();

    @Override
    public boolean isRegistered(String id) {
        return entries.containsKey(id);
    }

    @Override
    public void register(String id, Set<String> packages, ClassLoader classLoader) {

        if (isRegistered(id)) return;

        Entry entry = new Entry(packages, classLoader);
        entries.put(id, entry);
        packages.forEach(packageName -> idByPackages.put(packageName, id));

    }

    @Override
    public void unregister(String id) {

        if (!isRegistered(id)) return;

        Entry entry = entries.remove(id);
        entry.packages().forEach(idByPackages::remove);

    }

    @Override
    public Set<String> getPackagesById(String id) {
        if (!entries.containsKey(id)) return Set.of();
        return entries.get(id).packages();
    }

    @Override
    public ClassLoader getClassLoaderById(String id) {
        if (!entries.containsKey(id)) return null;
        return entries.get(id).classLoader;
    }

    @Override
    public String getIdByPackage(String packageName) {
        return idByPackages.get(packageName);
    }

}
