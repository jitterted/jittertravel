package dev.ted.jittertravel.application;

/**
 * A pointer to a stored event that referenced an audited location, carried so the zone audit can
 * show the full source event behind an unresolved location. {@code detail} is the event payload's
 * own {@code toString()} — the entire field set — and {@code sequence} locates it in the event log.
 */
public record EventReference(long sequence, String type, String detail) {
}
