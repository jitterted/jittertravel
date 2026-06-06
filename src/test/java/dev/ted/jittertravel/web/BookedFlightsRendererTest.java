package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.ChangeEntry;
import dev.ted.jittertravel.domain.FlightId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookedFlightsRendererTest {

    @Test
    void emptyFlightsRendersEmptyState() {
        String html = BookedFlightsRenderer.render(List.of());

        assertThat(html).contains("No flights booked yet.");
    }

    @Test
    void flightWithoutChangesRendersRouteAirlineAndFlightNumber() {
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges("Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59")
        ));

        assertThat(html)
                .contains("Sat, Jun 6, 1:55 PM")
                .contains("SFO→FRA")
                .contains("United")
                .contains("UA59");
    }

    @Test
    void flightWithChangesRendersHistoryItems() {
        String html = BookedFlightsRenderer.render(List.of(
                viewWithChanges("Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59",
                        "Booked on 2026-05-20 12:22PM",
                        "Changed on 2026-05-21 9:00AM")
        ));

        assertThat(html)
                .contains("Booked on 2026-05-20 12:22PM")
                .contains("Changed on 2026-05-21 9:00AM");
    }

    @Test
    void flightLinkPointsToChangeFlightUrl() {
        FlightId flightId = FlightId.random();
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges(flightId, "Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59")
        ));

        assertThat(html).contains("/booked-flights/" + flightId.id());
    }

    @Test
    void bookAnotherFlightLinkIsPresent() {
        String html = BookedFlightsRenderer.render(List.of());

        assertThat(html).contains("/book-flight");
    }

    private static BookedFlightView viewWithoutChanges(String display, String route,
                                                       String airline, String flightNumber) {
        return viewWithoutChanges(FlightId.random(), display, route, airline, flightNumber);
    }

    private static BookedFlightView viewWithoutChanges(FlightId flightId, String display,
                                                       String route, String airline,
                                                       String flightNumber) {
        return new BookedFlightView(
                flightId, airline, flightNumber, route,
                LocalDateTime.of(2026, 6, 6, 13, 55), display,
                List.of(new ChangeEntry(LocalDateTime.of(2026, 5, 20, 12, 22), "Booked on 2026-05-20 12:22PM"))
        );
    }

    private static BookedFlightView viewWithChanges(String display, String route,
                                                    String airline, String flightNumber,
                                                    String... historyEntries) {
        List<ChangeEntry> history = java.util.Arrays.stream(historyEntries)
                .map(text -> new ChangeEntry(LocalDateTime.now(), text))
                .toList();
        return new BookedFlightView(
                FlightId.random(), airline, flightNumber, route,
                LocalDateTime.of(2026, 6, 6, 13, 55), display,
                history
        );
    }
}
