package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Decision facts for {@link ChangeHotelCommand}: whether the booking being changed exists (folded
 * from the event stream) and the current instant used to validate the new check-in.
 */
public record ChangeHotelContext(boolean bookingExists, Instant now) implements DecisionContext {
}