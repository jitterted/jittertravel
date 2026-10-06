package dev.ted.jittertravel.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * The span of time a ground-transfer endpoint is "there": a stay's check-in through check-out, a
 * gathering's start through end. A flight or train leg is a window whose start and end are the same
 * moment ({@link #at}).
 * <p>
 * It exists for the date rule in {@link PlanGroundTransferCommand}: a ride from a hotel to a dinner
 * mid-stay happens on neither the check-in nor the check-out day, so a single moment per endpoint
 * would refuse the very ride the form exists to record (Ted, 2026-10-06).
 */
public record TransferEndpointWindow(ZonedTimestamp start, ZonedTimestamp end) {

    public static TransferEndpointWindow at(ZonedTimestamp moment) {
        return new TransferEndpointWindow(moment, moment);
    }

    /** Whether {@code date} is one of the window's days, each read in that end's own zone. */
    public boolean coversDay(LocalDate date) {
        return !date.isBefore(start.atEntryZone().toLocalDate())
               && !date.isAfter(end.atEntryZone().toLocalDate());
    }

    /** Zero when the windows touch or overlap, otherwise the time between the earlier end and the later start. */
    public Duration gapTo(TransferEndpointWindow other) {
        if (end.utc().isBefore(other.start.utc())) {
            return Duration.between(end.utc(), other.start.utc());
        }
        if (other.end.utc().isBefore(start.utc())) {
            return Duration.between(other.end.utc(), start.utc());
        }
        return Duration.ZERO;
    }
}
