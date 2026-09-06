package dev.ted.jittertravel.domain;

import java.time.Instant;


/**
 * {@code scheduledLegs} is every live flight and train already on the books, so the command can
 * refuse a journey that collides with one. Folded from the event stream, never from a projection
 * (R1) — see {@link ScheduledLegs}.
 */
public record ChangeFlightContext(boolean flightExists, Instant now,
                                  ScheduledLegs scheduledLegs) implements DecisionContext {
}
