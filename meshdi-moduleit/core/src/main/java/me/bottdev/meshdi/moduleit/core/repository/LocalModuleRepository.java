package me.bottdev.meshdi.moduleit.core.repository;

import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleRepository;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.core.URLModuleCandidate;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/// Implementation of [ModuleRepository] that works with JAR files stored locally in a specific directory.
public class LocalModuleRepository implements ModuleRepository {

    private final Path directory;

    /// @throws NullPointerException when provided path is null.
    /// @throws IllegalArgumentException when provided path is not a directory.
    public LocalModuleRepository(Path directory) {
        Objects.requireNonNull(directory, "Repository path must be non-null");
        if (!Files.isDirectory(directory))
            throw new IllegalArgumentException("Repository path must be a directory.");
        this.directory = directory;
    }

    private void createDirectoryIfNotExists() {
        if (Files.exists(directory)) return;
        File file = directory.toFile();
        file.mkdirs();
    }

    private boolean isModuleJar(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getJarEntry(ModuleDescriptor.DESCRIPTOR_FILE) != null;

        } catch (IOException ex) {
            return false;

        }
    }

    @Override
    public List<ModuleCandidate> candidates() throws CandidateListException {

        try {

            createDirectoryIfNotExists();

            List<ModuleCandidate> foundCandidates = new ArrayList<>();

            try (Stream<Path> children = Files.list(directory)) {
                children
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))
                        .filter(this::isModuleJar)
                        .map(path -> {
                            try {
                                URL url = path.toUri().toURL();
                                return new URLModuleCandidate(url, path);

                            } catch (MalformedURLException ex) {
                                throw new IllegalArgumentException(ex);

                            }
                        })
                        .forEach(foundCandidates::add);

            }

            return foundCandidates;

        } catch (IllegalArgumentException ex) {
            throw new CandidateListException("Failed to parse URL of candidate.", ex);

        } catch (IOException ex) {
            throw new CandidateListException("Failed to list candidates.", ex);

        } catch (SecurityException ex) {
            throw new CandidateListException("Security violation occurred.", ex);

        }


    }

}
