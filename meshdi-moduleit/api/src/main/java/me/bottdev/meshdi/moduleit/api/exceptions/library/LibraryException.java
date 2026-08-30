package me.bottdev.meshdi.moduleit.api.exceptions;

import lombok.Getter;

@Getter
public class LibraryFetchException extends ModuleException {

    private final String id;
    private final String url;

    public LibraryFetchException(String id, String url, String message) {
        super(message);
        this.id = id;
        this.url = url;
    }

    public LibraryFetchException(String id, String url, String message, Throwable cause) {
        super(message, cause);
        this.id = id;
        this.url = url;
    }

}
