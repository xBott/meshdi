package me.bottdev.meshdi.moduleit.core.utils;

import javax.tools.*;
import java.util.LinkedHashMap;
import java.util.Map;

class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

    private final Map<String, byte[]> outputs = new LinkedHashMap<>();

    InMemoryFileManager(StandardJavaFileManager delegate) { super(delegate); }

    Map<String, byte[]> outputs() { return outputs; }

    @Override
    public boolean hasLocation(Location location) {
        if (location == StandardLocation.CLASS_OUTPUT || location == StandardLocation.SOURCE_OUTPUT) {
            return true;
        }
        return super.hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className,
                                               JavaFileObject.Kind kind, FileObject sibling) {
        String path = className.replace('.', '/') + kind.extension;
        return new InMemoryOutputFile(path, kind, outputs);
    }

    @Override
    public FileObject getFileForOutput(Location location, String pkg, String relativeName, FileObject sibling) {
        String path = pkg.isEmpty() ? relativeName : pkg.replace('.', '/') + "/" + relativeName;
        return new InMemoryOutputFile(path, JavaFileObject.Kind.OTHER, outputs);
    }
}