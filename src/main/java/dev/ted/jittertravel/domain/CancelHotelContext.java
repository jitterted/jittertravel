package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Decision facts for {@link CancelHotelCommand}, folded from the authoritative event stream (never
 * from a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param bookingExists whether a live booking with this id exists: booked at some point and not
 *                      already cancelled.
 * @param checkIn       the booking's current check-in, or {@code null} to mean <em>no check-in
 *                      gate</em>. Making the absence explicit beats fabricating a check-in that is
 *                      never read.
 * @param now           the instant the cancellation is being attempted, captured at the boundary.
 */
public record CancelHotelContext(
        boolean bookingExists,
        ZonedTimestamp checkIn,
        Instant now
) implements DecisionContext {
}
