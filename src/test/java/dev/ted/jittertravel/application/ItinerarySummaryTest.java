package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightItineraryId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItinerarySummaryTest {

    /**
     * The band reads its route and dates from the first and last journey, so a summary with none
     * would fail there, deep in the render. It is refused on construction instead, as a
     * {@code Journey} with no legs is: an itinerary whose members the flights read model cannot
     * find is a log problem to hear about, not a band to leave blank.
     */
    @Test
    void aSummaryWithNoJourneysIsRefused() {
        assertThatThrownBy(() -> new ItinerarySummary(FlightItineraryId.of(UUID.randomUUID()), "MD7LKB",
                List.of(), 0, 0, false, false, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An itinerary summary has at least one journey");
    }
}
