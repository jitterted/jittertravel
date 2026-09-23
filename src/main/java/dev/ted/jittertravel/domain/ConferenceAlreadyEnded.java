package dev.ted.jittertravel.domain;

public class ConferenceAlreadyEnded extends RuntimeException {
    public ConferenceAlreadyEnded(String message) {
        super(message);
    }
}
