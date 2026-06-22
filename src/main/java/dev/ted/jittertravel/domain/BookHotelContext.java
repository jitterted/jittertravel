package dev.ted.jittertravel.domain;

import java.time.Instant;

public record BookHotelContext(Instant now) implements DecisionContext {
}
