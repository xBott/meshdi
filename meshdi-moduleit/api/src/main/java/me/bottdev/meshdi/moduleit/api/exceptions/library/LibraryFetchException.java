package me.bottdev.meshdi.moduleit.api.exceptions.library;

import lombok.Getter;

import java.net.URI;

@Getter
public class LibraryFetchException extends LibraryException {

    private final String id;
    private final URI uri;

    public LibraryFetchException(String id, URI uri, String message) {
        super(message);
        this.id = id;
        this.uri = uri;
    }

    public LibraryFetchException(String id, URI uri, String message, Throwable cause) {
        super(message, cause);
        this.id = id;
        this.uri = uri;
    }

}
