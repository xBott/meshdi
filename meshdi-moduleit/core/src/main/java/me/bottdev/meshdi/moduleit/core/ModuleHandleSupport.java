package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.api.ContextMesh;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;

import java.util.ArrayList;
import java.util.List;

/// Logic shared by module handle implementations.
final class ModuleHandleSupport {

    private ModuleHandleSupport() {}

    /// @return ids of the contexts the module's context should see in the mesh: every required dependency,
    /// and optional dependencies only when their context is registered. An absent optional dependency
    /// must not fail the start.
    static List<String> visibleContextIds(ModuleDescriptor descriptor, ContextMesh mesh) {
        List<String> sees = new ArrayList<>();
        for (VersionedDependencyRequest<String> dependency : descriptor.getVersionedDependencies()) {
            if (dependency.link() == DependencyLink.OPTIONAL && !mesh.contains(dependency.key())) continue;
            sees.add(dependency.key());
        }
        return sees;
    }

    /// Makes `loader` the thread context class loader until the returned scope is closed.
    /// Libraries that look classes up through the context class loader (`ServiceLoader`, JAXB, JDBC drivers,
    /// logging bindings) then find the module's classes instead of the host's.
    static ContextClassLoaderScope contextClassLoader(ClassLoader loader) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (loader != null) thread.setContextClassLoader(loader);
        return () -> thread.setContextClassLoader(previous);
    }

    @FunctionalInterface
    interface ContextClassLoaderScope extends AutoCloseable {
        @Override
        void close();
    }

}
