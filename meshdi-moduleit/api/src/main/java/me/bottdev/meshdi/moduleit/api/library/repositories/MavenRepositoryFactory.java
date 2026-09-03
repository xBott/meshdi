package me.bottdev.meshdi.moduleit.api.library.repositories;

import lombok.NonNull;
import me.bottdev.meshdi.moduleit.api.library.MavenRepository;

/**
 * Factory for creating instances of {@link MavenRepository}.
 * Encapsulates the HTTP client, local cache, and download manager dependencies.
 */
public interface MavenRepositoryFactory {

    /**
     * Creates a new Maven repository with the given ID and URL.
     *
     * @param id  the identifier of the repository
     * @param url the URL of the repository
     * @return the created Maven repository
     */
    MavenRepository create(@NonNull String id, @NonNull String url);

}
