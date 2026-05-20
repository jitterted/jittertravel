package dev.ted.jittertravel.application;

public class ReadOnlyModeException extends RuntimeException {
    public ReadOnlyModeException(String message) {
        super(message);
    }
}
