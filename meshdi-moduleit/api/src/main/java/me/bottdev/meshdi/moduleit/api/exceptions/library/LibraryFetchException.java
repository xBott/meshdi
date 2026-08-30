package me.bottdev.meshdi.moduleit.api.exceptions.library;

import lombok.Getter;

@Getter
public class LibraryException extends Exception {

    public LibraryException(String message) {
        super(message);
    }

    public LibraryException(String message, Throwable cause) {
        super(message, cause);
    }

}
