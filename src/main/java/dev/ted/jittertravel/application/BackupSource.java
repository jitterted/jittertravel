package dev.ted.jittertravel.application;

/**
 * The label a backup records for where it was produced: {@code "production"} when the app runs in a
 * hosted environment (Railway injects {@code RAILWAY_ENVIRONMENT_NAME}), {@code "local"} otherwise.
 * Resolved once at the config boundary from that environment marker and injected as a fixed value —
 * an instance is production or local for the whole of its life.
 */
public class BackupSource {

    private final String label;

    public BackupSource(String environmentMarker) {
        this.label = (environmentMarker == null || environmentMarker.isBlank()) ? "local" : "production";
    }

    public String label() {
        return label;
    }
}
