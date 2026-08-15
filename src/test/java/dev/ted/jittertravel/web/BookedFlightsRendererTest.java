package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.ChangeEntry;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

        // Date and time are separate .nowrap spans so a narrow cell breaks only between them
        // (never mid-value); there is no longer a comma joining date and time.
        assertThat(html)
                .contains("<span class=\"nowrap\">Sat, Jun 6</span>")
                .contains("<span class=\"nowrap\">1:55 PM</span>")
                .contains("<span class=\"nowrap\">Sun, Jun 7</span>")
                .contains("<span class=\"nowrap\">9:45 AM</span>")
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
                .contains("<span class=\"nowrap\">Sun, Jun 7</span>")
                .contains("<a class=\"flight-edit-link\" href=\"/booked-flights/" + flightId.id() + "\">Edit</a>");
    }

    @Test
    void narrowViewportCollapsesTheGridWithoutHorizontalScroll() {
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges("Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59")
        ), TimeView.FUTURE);

        // No page may ever scroll sideways: the width cap is gone and the seven-column grid
        // collapses to a single stacked column under a media query, revealing per-leg labels
        // (hidden on desktop, where the column header carries them) so the stacked cells stay
        // unambiguous.
        assertThat(html)
                .doesNotContain("max-width: 100ch")
                .contains("grid-template-columns: 1fr")
                .contains("<span class=\"leg-label\">Departure</span>")
                .contains("<span class=\"leg-label\">Arrival</span>")
                .contains("<span class=\"leg-label\">Route</span>")
                .contains("<span class=\"leg-label\">Airline</span>")
                .contains("<span class=\"leg-label\">Flight Number</span>");
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

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static BookedFlightView viewWithoutChanges(FlightId flightId, String display,
                                                       String route, String airline,
                                                       String flightNumber) {
        return new BookedFlightView(
                flightId, airline, flightNumber, route,
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 6, 13, 55), UTC),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 7, 9, 45), UTC),
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
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 6, 13, 55), UTC),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 7, 9, 45), UTC),
                history
        );
    }
}
