package com.library.service;

/** Raised when a requested operation violates a business rule or input is invalid. */
public class LibraryException extends RuntimeException {

    public LibraryException(String message) {
        super(message);
    }

    public LibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
