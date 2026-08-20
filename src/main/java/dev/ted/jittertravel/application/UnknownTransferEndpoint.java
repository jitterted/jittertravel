package dev.ted.jittertravel.application;

/**
 * A ground-transfer endpoint token that does not resolve: a token that is neither {@code airport:}
 * nor {@code hotel:}, or a hotel booking id that no longer exists — the stay was cancelled between
 * the form being rendered and being submitted. Surfaces as a field error on the form, never a 500.
 */
public class UnknownTransferEndpoint extends RuntimeException {
    public UnknownTransferEndpoint(String message) {
        super(message);
    }
}
