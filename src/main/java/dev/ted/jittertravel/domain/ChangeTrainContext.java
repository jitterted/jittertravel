package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Decision facts for {@link ChangeTrainCommand}: whether the trip being changed exists (folded
 * from the event stream) and the current instant used to validate the new departure.
 */
public record ChangeTrainContext(boolean tripExists, Instant now) implements DecisionContext {
}