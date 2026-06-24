package dev.ted.jittertravel.domain;

import java.time.Instant;

public record ChangeFlightContext(boolean flightExists, Instant now) implements DecisionContext {
}
