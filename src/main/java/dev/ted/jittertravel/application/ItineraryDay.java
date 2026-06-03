package dev.ted.jittertravel.application;

import java.time.LocalDate;
import java.util.List;

public record ItineraryDay(LocalDate date, List<ItineraryEntry> entries) {
    public boolean hasEntries() { return !entries.isEmpty(); }
}
