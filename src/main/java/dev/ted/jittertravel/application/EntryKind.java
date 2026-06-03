package dev.ted.jittertravel.application;

/**
 * The category of a calendar entry. Lanes are rendered top-to-bottom in
 * declaration order, so the ordering here is significant.
 */
public enum EntryKind {
    CONFERENCE,
    MEETUP,
    FLIGHT,
    TRAIN,
    LODGING
}
