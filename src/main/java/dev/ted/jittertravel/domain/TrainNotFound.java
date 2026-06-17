package dev.ted.jittertravel.domain;

public class TrainNotFound extends RuntimeException {
    public TrainNotFound(String message) {
        super(message);
    }
}