package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.ChangeEntry;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.FlightId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookedFlightsRendererTest {

    @Test
    void emptyAllListRendersBookedYetMessage() {
        String html = BookedFlightsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html).contains("No flights booked yet.");
    }

    @Test
    void emptyFutureListRendersNoUpcomingMessage() {
        String html = BookedFlightsRenderer.render(List.of(), TimeView.FUTURE);

        assertThat(html).contains("No upcoming flights.");
    }

    @Test
    void activeFilterMarkedOnToggleLink() {
        String html = BookedFlightsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("<a href=\"/booked-flights?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/booked-flights?filter=future\">Upcoming</a>");
    }

    @Test
    void flightWithoutChangesRendersRouteAirlineAndFlightNumber() {
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges("Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("Sat, Jun 6, 1:55 PM")
                .contains("Sun, Jun 7, 9:45 AM")
                .contains("SFO→FRA")
                .contains("United")
                .contains("UA59");
    }

    @Test
    void flightRowRendersArrivalAndASeparateEditLink() {
        FlightId flightId = FlightId.random();
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges(flightId, "Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("Sun, Jun 7, 9:45 AM")
                .contains("<a class=\"flight-edit-link\" href=\"/booked-flights/" + flightId.id() + "\">Edit</a>");
    }

    @Test
    void flightWithChangesRendersHistoryItems() {
        String html = BookedFlightsRenderer.render(List.of(
                viewWithChanges("Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59",
                        "Booked on 2026-05-20 12:22PM",
                        "Changed on 2026-05-21 9:00AM")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("Booked on 2026-05-20 12:22PM")
                .contains("Changed on 2026-05-21 9:00AM");
    }

    @Test
    void flightLinkPointsToChangeFlightUrl() {
        FlightId flightId = FlightId.random();
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges(flightId, "Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59")
        ), TimeView.FUTURE);

        assertThat(html).contains("/booked-flights/" + flightId.id());
    }

    @Test
    void bookAnotherFlightLinkIsPresent() {
        String html = BookedFlightsRenderer.render(List.of(), TimeView.ALL);

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
                LocalDateTime.of(2026, 6, 6, 13, 55), display, "Sun, Jun 7, 9:45 AM",
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
                LocalDateTime.of(2026, 6, 6, 13, 55), display, "Sun, Jun 7, 9:45 AM",
                history
        );
    }
}
