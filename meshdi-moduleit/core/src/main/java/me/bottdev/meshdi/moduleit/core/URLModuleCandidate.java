package me.bottdev.meshdi.moduleit.core;

import me.bottdev.kern.commons.Lazy;
import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleLoadEnvironment;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static me.bottdev.meshdi.moduleit.api.ModuleDescriptor.DESCRIPTOR_FILE;

/// Implementation of [ModuleCandidate] for local JAR files.
/// Descriptor loading is lazy.
public final class URLModuleCandidate implements ModuleCandidate {

    private final URL url;
    private final Path path;
    private final String sourceKey;
    private Lazy<ModuleDescriptor> descriptor = Lazy.of(this::readDescriptor);

    public URLModuleCandidate(URL url, Path path) {
        this.url = url;
        this.path = path;
        this.sourceKey = path.getFileName().toString().toLowerCase();
    }

    @Override
    public String sourceKey() {
        return sourceKey;
    }

    private String readModuleClassName() {

        try (JarFile jar = new JarFile(path.toFile())) {

            JarEntry entry = jar.getJarEntry(DESCRIPTOR_FILE);
            if (entry == null)
                throw new ModuleDescriptorException("Missing " + DESCRIPTOR_FILE + " in jar: " + path);

            try (InputStream in = jar.getInputStream(entry)) {

                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (content.isEmpty())
                    throw new ModuleDescriptorException(DESCRIPTOR_FILE + " is empty in jar: " + path);

                return content;

            }

        } catch (IOException ex) {
            throw new ModuleDescriptorException("Failed to read " + DESCRIPTOR_FILE + " from: " + path, ex);

        }
    }

    private ModuleDescriptor readDescriptor() {
        String className = readModuleClassName();

        try (URLClassLoader probeLoader = new URLClassLoader(
                new URL[]{ url },
                getClass().getClassLoader()
        )) {

            Class<?> descriptorClass = Class.forName(className, false, probeLoader);

            return (ModuleDescriptor) descriptorClass.getDeclaredConstructor().newInstance();

        } catch (ClassNotFoundException ex) {
            throw new ModuleDescriptorException("Module class '" + className + "' declared in " + DESCRIPTOR_FILE + " not found in jar: " + path, ex);

        } catch (IOException ex) {
            throw new ModuleDescriptorException("Failed to open jar for descriptor reading: " + path, ex);

        } catch (Exception ex) {
            throw new ModuleDescriptorException("Failed to create module descriptor.", ex);

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