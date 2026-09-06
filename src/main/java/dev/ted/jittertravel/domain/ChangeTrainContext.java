package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Decision facts for {@link ChangeTrainCommand}: whether the trip being changed exists (folded
 * from the event stream), the current instant used to validate the new departure, and every live
 * scheduled leg, so a change that collides with another journey is refused.
 * <p>
 * The collision check excludes the trip being changed — see {@link ScheduledLegs#overlapping} —
 * without which no booked trip could ever be corrected, every edit colliding with itself.
 */
public record ChangeTrainContext(boolean tripExists, Instant now,
                                 ScheduledLegs scheduledLegs) implements DecisionContext {
}