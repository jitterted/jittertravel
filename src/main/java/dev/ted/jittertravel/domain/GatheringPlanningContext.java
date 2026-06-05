package dev.ted.jittertravel.domain;

import java.time.LocalDate;

public record GatheringPlanningContext(LocalDate today) implements DecisionContext {
}
