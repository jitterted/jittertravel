package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

public record BookHotelContext(LocalDateTime now) implements DecisionContext {
}
