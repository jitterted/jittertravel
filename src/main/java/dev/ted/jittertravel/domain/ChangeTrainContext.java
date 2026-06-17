package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

/**
 * Decision facts for {@link ChangeTrainCommand}: whether the trip being changed exists (folded
 * from the event stream) and the current time used to validate the new departure.
 */
public record ChangeTrainContext(boolean tripExists, LocalDateTime now) implements DecisionContext {
}