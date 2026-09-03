package me.bottdev.meshdi.moduleit.core.library;

import lombok.NonNull;
import me.bottdev.kern.commons.download.DownloadManager;
import me.bottdev.meshdi.moduleit.api.library.MavenRepository;
import me.bottdev.meshdi.moduleit.api.library.repositories.LocalMavenCache;
import me.bottdev.meshdi.moduleit.api.library.repositories.MavenRepositoryFactory;
import me.bottdev.meshdi.moduleit.api.library.repositories.RemoteMavenRepository;

import java.net.http.HttpClient;

/**
 * Standard implementation of {@link MavenRepositoryFactory} that creates {@link RemoteMavenRepository}.
 */
public class RemoteMavenRepositoryFactory implements MavenRepositoryFactory {

    private final HttpClient httpClient;
    private final LocalMavenCache localCache;
    private final DownloadManager downloadManager;

    public RemoteMavenRepositoryFactory(
            @NonNull HttpClient httpClient,
            @NonNull LocalMavenCache localCache,
            @NonNull DownloadManager downloadManager
    ) {
        this.httpClient = httpClient;
        this.localCache = localCache;
        this.downloadManager = downloadManager;
    }

    @Override
    public MavenRepository create(@NonNull String id, @NonNull String url) {
        return new RemoteMavenRepository(id, url, httpClient, localCache, downloadManager);
    }

}
