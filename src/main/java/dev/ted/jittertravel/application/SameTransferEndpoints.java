package dev.ted.jittertravel.application;

/**
 * A ground transfer whose origin and destination are the same place. It records no journey, so it
 * would add a presence fact that says nothing while still occupying a calendar lane. Rejected at
 * the boundary and shown on the form.
 */
public class SameTransferEndpoints extends RuntimeException {
    public SameTransferEndpoints(String message) {
        super(message);
    }
}
