package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Decision facts for {@link PlanGatheringCommand}: the moment the command was issued, captured at
 * the boundary. An {@link Instant} (rather than a {@code LocalDate}) because the server's zone must
 * not enter the decision — the command turns it into a calendar date in the <em>gathering's</em>
 * zone when it checks that the date is still in the future.
 */
public record GatheringPlanningContext(Instant now) implements DecisionContext {
}
