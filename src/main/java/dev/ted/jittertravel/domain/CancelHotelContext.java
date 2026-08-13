package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link CancelHotelCommand}, folded from the authoritative event stream (never
 * from a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param bookingExists whether a live booking with this id exists: booked at some point and not
 *                      already cancelled. The command has no other input — cancelling is not gated
 *                      on check-in or on the free-cancellation deadline, so no clock is needed.
 */
public record CancelHotelContext(
        boolean bookingExists
) implements DecisionContext {
}
