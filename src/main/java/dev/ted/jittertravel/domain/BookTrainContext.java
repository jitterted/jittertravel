package dev.ted.jittertravel.domain;

import java.time.Instant;

public record BookTrainContext(Instant now) implements DecisionContext {
}
