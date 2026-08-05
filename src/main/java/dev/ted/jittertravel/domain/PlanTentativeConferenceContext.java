package dev.ted.jittertravel.domain;

import java.time.Instant;

public record PlanTentativeConferenceContext(Instant now) implements DecisionContext {
}
