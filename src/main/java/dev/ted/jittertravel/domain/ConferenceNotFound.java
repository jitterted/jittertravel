package dev.ted.jittertravel.domain;

public class ConferenceNotFound extends RuntimeException {
    public ConferenceNotFound(String message) {
        super(message);
    }
}
