package dev.ted.jittertravel.domain;

import java.time.Instant;

public record PlanConferenceContext(Instant now) implements DecisionContext {
}
