package me.bottdev.meshdi.moduleit.core.library;

import lombok.NonNull;
import me.bottdev.kern.commons.download.*;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;
import me.bottdev.meshdi.moduleit.api.library.MavenRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Optional;

public class RemoteMavenRepository implements MavenRepository {

    private final String id;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final LocalMavenCache cache;
    private final DownloadManager downloadManager;

    public RemoteMavenRepository(
            @NonNull String id,
            @NonNull String baseUrl,
            @NonNull HttpClient httpClient,
            @NonNull LocalMavenCache cache
    ) {
        this.id = id;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
        this.cache = cache;
        this.downloadManager = new ParallelDownloadManager(httpClient);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Optional<Path> fetchPom(MavenCoordinate coordinate) {
        return fetch(coordinate, "pom");
    }

    @Override
    public Optional<Path> fetchArtifact(MavenCoordinate coordinate) {
        return fetch(coordinate, "jar");
    }

    private Optional<Path> fetch(MavenCoordinate coordinate, String extension) {

        String url = baseUrl + "/" + coordinate.repositoryPath(extension);
        URI uri = URI.create(url);
        Path target = cache.root().resolve(coordinate.repositoryPath(extension));

        DownloadTask task = downloadManager.download(uri, target,
                DownloadOptions.builder()
                        .overwrite(true)
                        .bufferSize(8192)
                        .listener(new DownloadProgressListener() {
                            @Override
                            public void onStarted(DownloadKey key, long totalBytes) {
                                System.out.println("Download started: " + key + ", " + totalBytes + "B");
                            }

                            @Override
                            public void onProgress(DownloadKey key, long downloadedBytes, long totalBytes, float speedBytesPerSecond) {
                                System.out.println("Download progress: " + key + ", " + downloadedBytes + "B" + " / " + totalBytes + "B " + speedBytesPerSecond + "B/s");

                            }

                            @Override
                            public void onCompleted(DownloadKey key, DownloadResult result) {
                                System.out.println("Download completed: " + key);

                            }
                        })
                        .build()
        );

        try {
            DownloadResult result = task.completion().join();
            Path path = result.path();

            return Optional.of(path);

        } catch (Exception ex) {
            return Optional.empty();

        }

    }

}
