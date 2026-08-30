package me.bottdev.meshdi.moduleit.api.exceptions.library;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class LibraryFetchException extends LibraryException {

    private final String id;
    private final String url;

    public LibraryFetchException(
            @NonNull String id,
            @NonNull String url,
            String message
    ) {
        super(message);
        this.id = id;
        this.url = url;
    }

    public LibraryFetchException(
            @NonNull String id,
            @NonNull String url,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.id = id;
        this.url = url;
    }

}
