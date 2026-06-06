package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmedCalendarRendererTest {

    @Test
    void emptyEntriesRendersCalendarPage() {
        String html = ConfirmedCalendarRenderer.render(List.of(), false);

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

        String html = ConfirmedCalendarRenderer.render(List.of(hotel), true);

        assertThat(html)
                .contains("Hotel")
                .doesNotContain("Grand Hotel");
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

        String html = ConfirmedCalendarRenderer.render(List.of(hotel), false);

        assertThat(html).contains("Grand Hotel");
    }
}
