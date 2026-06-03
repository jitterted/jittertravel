package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

public record BookTrainContext(LocalDateTime now) implements DecisionContext {
}
