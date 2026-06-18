package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmedCalendarRendererTest {

    @Test
    void emptyEntriesRendersCalendarPage() {
        String html = ConfirmedCalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false);

        assertThat(html)
                .contains("Confirmed Calendar")
                .contains("JitterTravel");
    }

    @Test
    void publicUserSeesRedactedHotelName() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING,
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                "Grand Hotel", List.of("Berlin, Germany"),
                "Grand Hotel cont'd", List.of("Berlin, Germany"),
                "https://maps.google.com/grand"
        );

        String html = ConfirmedCalendarRenderer.render(List.of(hotel), LocalDate.of(2026, 6, 11), true);

        assertThat(html)
                .contains("Hotel")
                .doesNotContain("Grand Hotel");
    }

    @Test
    void ownerSeesEditLinkOnTrainEntry() {
        CalendarEntry train = new CalendarEntry(
                EntryKind.TRAIN,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 13, 0),
                "🚄 London → Manchester", List.of("9:00 AM → 1:00 PM"),
                null, null, null, "/booked-trains/trip-123"
        );

        String html = ConfirmedCalendarRenderer.render(List.of(train), LocalDate.of(2026, 6, 11), false, true);

        assertThat(html).contains("href=\"/booked-trains/trip-123\"");
    }

    @Test
    void nonOwnerSeesNoEditLinkOnTrainEntry() {
        CalendarEntry train = new CalendarEntry(
                EntryKind.TRAIN,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 13, 0),
                "🚄 London → Manchester", List.of("9:00 AM → 1:00 PM"),
                null, null, null, "/booked-trains/trip-123"
        );

        String html = ConfirmedCalendarRenderer.render(List.of(train), LocalDate.of(2026, 6, 11), false, false);

        assertThat(html).doesNotContain("href=\"/booked-trains/");
    }

    @Test
    void ownerSeesEditLinkOnFlightEntry() {
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 13, 0),
                "✈️ SFO→JFK", List.of("9:00 AM → 1:00 PM"),
                null, null, null, "/booked-flights/flight-123"
        );

        String html = ConfirmedCalendarRenderer.render(List.of(flight), LocalDate.of(2026, 6, 11), false, true);

        assertThat(html).contains("href=\"/booked-flights/flight-123\"");
    }

    @Test
    void authenticatedUserSeesFullHotelName() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING,
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                "Grand Hotel", List.of("Berlin, Germany"),
                "Grand Hotel cont'd", List.of("Berlin, Germany"),
                "https://maps.google.com/grand"
        );

        String html = ConfirmedCalendarRenderer.render(List.of(hotel), LocalDate.of(2026, 6, 11), false);

        assertThat(html).contains("Grand Hotel");
    }
}
