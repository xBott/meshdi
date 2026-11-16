package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.Lazy;
import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptorCodec;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static me.bottdev.meshdi.moduleit.api.ModuleDescriptor.DESCRIPTOR_FILE;

/// Implementation of [ModuleCandidate] for local JAR files.
/// Descriptor loading is lazy.
public final class URLModuleCandidate implements ModuleCandidate {

    private final URL url;
    private final Path path;
    private final String sourceKey;
    private final Lazy<ModuleDescriptor> descriptor = Lazy.of(this::readDescriptor);

    public URLModuleCandidate(URL url, Path path) {
        this.url = url;
        this.path = path;
        this.sourceKey = path.getFileName().toString().toLowerCase();
    }

    @Override
    public String sourceKey() {
        return sourceKey;
    }

    /// Reads the descriptor as data. No class of the jar is loaded and no code of the jar runs,
    /// so resolving candidates is safe even for a jar that should never be started.
    private ModuleDescriptor readDescriptor() {
        try (JarFile jar = new JarFile(path.toFile())) {

            JarEntry entry = jar.getJarEntry(DESCRIPTOR_FILE);
            if (entry == null)
                throw new ModuleDescriptorException("Missing " + DESCRIPTOR_FILE + " in jar: " + path);

            try (InputStream in = jar.getInputStream(entry)) {
                byte[] bytes = in.readNBytes(ModuleDescriptorCodec.MAX_SIZE_BYTES + 1);
                if (bytes.length > ModuleDescriptorCodec.MAX_SIZE_BYTES)
                    throw new ModuleDescriptorException(DESCRIPTOR_FILE + " is too large in jar: " + path);

                String content = new String(bytes, StandardCharsets.UTF_8);
                if (content.isBlank())
                    throw new ModuleDescriptorException(DESCRIPTOR_FILE + " is empty in jar: " + path);

                try {
                    return ModuleDescriptorCodec.decode(content);

                } catch (ModuleDescriptorException ex) {
                    throw new ModuleDescriptorException(
                            "Invalid " + DESCRIPTOR_FILE + " in jar " + path + ": " + ex.getMessage(), ex
                    );

                }
            }

        } catch (IOException ex) {
            throw new ModuleDescriptorException("Failed to read " + DESCRIPTOR_FILE + " from: " + path, ex);
        }
    }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptor.compute();
    }

    @Override
    public URL sourceUrl() {
        return url;
    }

}