package dev.ted.jittertravel.domain;

/**
 * Why a flight left the books, as a fact the decision folds can read — not the free-text
 * {@code reason}, which is for recall and which nothing keys off.
 * <p>
 * The difference matters to one rule: a pasted schedule change that lists a leg an earlier change
 * dropped is the airline putting it back, so the leg is booked again; a leg Ted cancelled himself
 * is not quietly resurrected by a paste (see {@link LegCancelledEarlier}).
 */
public enum FlightCancellationCause {
    /** Cancelled by Ted, alone or as part of cancelling a whole trip. Also what every older event reads as. */
    MANUAL,
    /** Dropped by applying an airline's schedule change to its itinerary. */
    AIRLINE_SCHEDULE_CHANGE
}
