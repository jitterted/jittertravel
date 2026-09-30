package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link CancelFlightCommand}, folded from the authoritative event stream (never
 * from a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param flightExists whether a live flight with this id exists: booked at some point and not
 *                     already cancelled. No clock, because cancelling is not time-gated.
 */
public record CancelFlightContext(
        boolean flightExists
) implements DecisionContext {
}
