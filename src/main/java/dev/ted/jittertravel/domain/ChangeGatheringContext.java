package dev.ted.jittertravel.domain;

import java.time.LocalDate;

/**
 * Decision facts for {@link ChangeGatheringCommand}: whether the gathering being changed exists
 * (read from the details projection) and today's date used to validate the new gathering date.
 */
public record ChangeGatheringContext(boolean gatheringExists, LocalDate today) implements DecisionContext {
}
