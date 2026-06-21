package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

/**
 * Decision facts for {@link ChangeHotelCommand}: whether the booking being changed exists (folded
 * from the event stream) and the current time used to validate the new check-in.
 */
public record ChangeHotelContext(boolean bookingExists, LocalDateTime now) implements DecisionContext {
}