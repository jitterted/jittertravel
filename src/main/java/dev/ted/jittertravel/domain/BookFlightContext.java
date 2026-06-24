package dev.ted.jittertravel.domain;

import java.time.Instant;

public record BookFlightContext(Instant now) implements DecisionContext {
}
