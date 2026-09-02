package me.bottdev.meshdi.moduleit.api.library.repositories;

import lombok.NonNull;
import me.bottdev.kern.commons.download.*;
import me.bottdev.kern.commons.download.exceptions.DownloadNotFoundException;
import me.bottdev.meshdi.moduleit.api.exceptions.library.LibraryFetchException;
import me.bottdev.meshdi.moduleit.api.library.MavenCoordinate;
import me.bottdev.meshdi.moduleit.api.library.MavenRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
            @NonNull LocalMavenCache cache,
            @NonNull DownloadManager downloadManager
    ) {
        this.id = id;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
        this.cache = cache;
        this.downloadManager = downloadManager;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Optional<Path> fetchPom(MavenCoordinate coordinate) throws LibraryFetchException {
        return fetch(coordinate, "pom");
    }

    @Override
    public Optional<Path> fetchArtifact(MavenCoordinate coordinate) throws LibraryFetchException {
        return fetch(coordinate, "jar");
    }

    @Override
    public CompletableFuture<Optional<Path>> fetchArtifactAsync(MavenCoordinate coordinate) {
        return fetchAsync(coordinate, "jar");
    }

    private Optional<Path> fetch(MavenCoordinate coordinate, String extension) throws LibraryFetchException {
        try {
            return fetchAsync(coordinate, extension).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof LibraryFetchException) {
                throw (LibraryFetchException) ex.getCause();
            }
            throw new LibraryFetchException(id(), URI.create(baseUrl), "Unexpected cause during fetch", ex.getCause() != null ? ex.getCause() : ex);
        }
    }

    private CompletableFuture<Optional<Path>> fetchAsync(MavenCoordinate coordinate, String extension) {
        String urlPath = coordinate.repositoryPath(extension);

        if (coordinate.version().endsWith("-SNAPSHOT")) {
            urlPath = resolveSnapshotPath(coordinate, extension, urlPath);
        }

        String url = baseUrl + "/" + urlPath;
        URI uri = URI.create(url);
        Path target = cache.root().resolve(coordinate.repositoryPath(extension));

        DownloadTask task = downloadManager.download(uri, target,
                DownloadOptions.builder()
                        .overwrite(true)
                        .checksum(new DownloadChecksum(
                                DownloadChecksum.Algorithm.SHA_256,
                                () -> fetchHash(url)
                        ))
                        .bufferSize(8192)
                        .build()
        );

        return task.completion().handle((result, ex) -> {

            if (ex != null) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause.getClass() == DownloadNotFoundException.class) return Optional.empty();

                throw new CompletionException(new LibraryFetchException(id(), uri, "Failed to fetch library from remote maven repository.", cause));
            }

            return Optional.of(result.path());

        });
    }

    private String resolveSnapshotPath(MavenCoordinate coordinate, String extension, String defaultPath) {
        try {
            String metadataUrl = baseUrl + "/" + coordinate.metadataPath();
            URI metadataUri = URI.create(metadataUrl);
            Path metadataTarget = cache.root().resolve(coordinate.metadataPath());
            
            DownloadTask metadataTask = downloadManager.download(metadataUri, metadataTarget,
                    DownloadOptions.builder().overwrite(true).build());
            DownloadResult metadataResult = metadataTask.completion().join();
            
            String resolvedVersion = me.bottdev.meshdi.moduleit.api.library.MavenMetadataParser.resolveSnapshotVersion(
                    metadataResult.path(), coordinate.version());
                    
            return coordinate.snapshotRepositoryPath(extension, resolvedVersion);

        } catch (Exception ex) {
            if (ex.getCause() == null || ex.getCause().getClass() != DownloadNotFoundException.class) {
                System.err.println("Warning: failed to fetch/parse maven-metadata.xml for " + coordinate + ", falling back to regular path. " + ex.getMessage());
            }
            return defaultPath;

        }

    }

    private String fetchHash(String url) {

        String hashUrl = url + ".sha256";
        URI uri = URI.create(hashUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() != 200) {
                throw new IOException("Unexpected HTTP status " + response.statusCode() + " for " + url);

            }

            return response.body();

        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException("Failed to fetch checksum hash for " + url, ex);

        }

    }

}
