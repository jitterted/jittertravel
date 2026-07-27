package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Decision facts for {@link ChangeGatheringCommand}: whether the gathering being changed exists
 * (read from the details projection) and the moment the command was issued, used to validate that
 * the new gathering date is still in the future. See {@link GatheringPlanningContext} for why this
 * is an {@link Instant}.
 */
public record ChangeGatheringContext(boolean gatheringExists, Instant now) implements DecisionContext {
}
