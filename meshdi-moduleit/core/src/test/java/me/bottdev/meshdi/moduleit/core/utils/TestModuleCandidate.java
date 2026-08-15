package me.bottdev.meshdi.moduleit.core.utils;

import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleLoadEnvironment;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/// A fake [ModuleCandidate] for unit tests. Wraps a pre-built descriptor and lets
/// tests control classloader creation and descriptor-read failures without touching
/// the filesystem or a real classloader.
public final class TestModuleCandidate implements ModuleCandidate {

    private final String sourceKey;
    private final Supplier<ModuleDescriptor> descriptorSupplier;
    private final Function<List<String>, ClassLoader> classLoaderFactory;

    private TestModuleCandidate(Builder builder) {
        this.sourceKey = builder.sourceKey;
        this.descriptorSupplier = builder.descriptorSupplier;
        this.classLoaderFactory = builder.classLoaderFactory;
    }

    @Override
    public String sourceKey() { return sourceKey; }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptorSupplier.get(); // может бросить ModuleDescriptorException — см. Builder.brokenDescriptor
    }

    @Override
    public ClassLoader openClassLoader(ModuleLoadEnvironment environment, List<String> dependencies) {
        return classLoaderFactory.apply(dependencies);
    }

    @Override
    public String toString() {
        return "TestModuleCandidate[" + sourceKey + "]";
    }

    /// Shortcut: build a candidate directly from a ready descriptor,
    /// with a no-op classloader (getSystemClassLoader by default).
    public static TestModuleCandidate of(ModuleDescriptor descriptor) {
        return builder(descriptor.id() + "-" + descriptor.version() + ".jar")
                .descriptor(descriptor)
                .build();
    }

    public static Builder builder(String sourceKey) {
        return new Builder(sourceKey);
    }

    public static final class Builder {

        private final String sourceKey;
        private Supplier<ModuleDescriptor> descriptorSupplier;
        private Function<List<String>, ClassLoader> classLoaderFactory =
                deps -> ClassLoader.getSystemClassLoader(); // безобидный дефолт для тестов, где classLoader не важен

        private Builder(String sourceKey) {
            this.sourceKey = sourceKey;
        }

        public Builder descriptor(ModuleDescriptor descriptor) {
            this.descriptorSupplier = () -> descriptor;
            return this;
        }

        /// Lazily-failing descriptor — simulates a corrupted/invalid module jar.
        /// The exception is thrown only when descriptor() is actually called,
        /// mirroring real lazy-parsing behavior (see LocalModuleCandidate).
        public Builder brokenDescriptor(String reason) {
            this.descriptorSupplier = () -> {
                throw new ModuleDescriptorException(reason);
            };
            return this;
        }

        public Builder classLoader(ClassLoader fixed) {
            this.classLoaderFactory = deps -> fixed;
            return this;
        }

        public Builder classLoader(Function<List<String>, ClassLoader> factory) {
            this.classLoaderFactory = factory;
            return this;
        }

        /// Simulates a candidate whose classloader fails to open —
        /// e.g. a broken jar entry, missing class, IO error.
        public Builder failingClassLoader(RuntimeException toThrow) {
            this.classLoaderFactory = deps -> { throw toThrow; };
            return this;
        }

        public TestModuleCandidate build() {
            if (descriptorSupplier == null) {
                throw new IllegalStateException("TestModuleCandidate requires a descriptor() or brokenDescriptor()");
            }
            return new TestModuleCandidate(this);
        }
    }
}