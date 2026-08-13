package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Decision facts for {@link PlanPrivateEventCommand}: the moment the command was issued, captured
 * at the boundary. An {@link Instant} (not a {@code LocalDate}) because the server's zone must not
 * enter the decision — the command turns it into a calendar date in the <em>event's</em> zone when
 * it checks the date is still in the future. Mirrors {@code GatheringPlanningContext}.
 */
public record PlanPrivateEventContext(Instant now) implements DecisionContext {
}
