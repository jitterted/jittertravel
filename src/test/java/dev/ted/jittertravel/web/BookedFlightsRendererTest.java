package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.CancelledView;
import dev.ted.jittertravel.application.ChangeEntry;
import dev.ted.jittertravel.application.FlightTrip;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    void createLinkGoesToTheBookFlightForm() {
        String html = BookedFlightsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("<a class=\"create-link\" href=\"/book-flight\">Book another flight</a>");
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
    void flightRowOffersCancelAfterEdit() {
        FlightId flightId = FlightId.random();
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges(flightId, "Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("<a class=\"flight-cancel-link\" href=\"/booked-flights/"
                        + flightId.id() + "/cancel\">Cancel</a>");
        assertThat(html.indexOf("<a class=\"flight-edit-link\""))
                .as("Edit keeps its position; Cancel is appended after it")
                .isLessThan(html.indexOf("<a class=\"flight-cancel-link\""));
        assertThat(html)
                .as("red, because there is no undo")
                .contains(".flight-cancel-link { font-size: 0.85rem; color: #b00;");
    }

    @Test
    void theCancelledSwitchWhileHiddenCountsWhatItLeavesOutAndLinksToShowingThem() {
        String html = BookedFlightsRenderer.render(List.of(), TimeView.FUTURE, CancelledView.HIDE, 2);

        assertThat(html)
                .contains("<a class=\"flight-cancelled-toggle\" role=\"button\" aria-pressed=\"false\"")
                .contains("href=\"/booked-flights?filter=future&amp;cancelled=show\"")
                .contains("Show cancelled<b>2</b>");
    }

    @Test
    void theCancelledSwitchWhileShownLinksBackToHidingAndTheTimeToggleKeepsIt() {
        String html = BookedFlightsRenderer.render(List.of(), TimeView.ALL, CancelledView.SHOW, 2);

        assertThat(html)
                .contains("aria-pressed=\"true\"")
                .contains("href=\"/booked-flights?filter=all\"")
                .as("changing the time filter must not silently drop the cancelled switch")
                .contains("href=\"/booked-flights?filter=future&amp;cancelled=show\"");
    }

    @Test
    void aCancelledFlightIsMarkedWithItsDateAndReasonAndItsActionsAreGreyed() {
        FlightId flightId = FlightId.random();
        BookedFlightView cancelled = cancelledView(flightId,
                Instant.parse("2026-05-20T18:00:00Z"), "Credit on file");

        String html = BookedFlightsRenderer.render(List.of(cancelled), TimeView.ALL, CancelledView.SHOW, 1);

        assertThat(html)
                .contains("class=\"flight-card flight-card-row flight-card--cancelled\"")
                .as("when and why, in one box")
                .contains("<span class=\"flight-cancelled-badge\"><span class=\"flight-cancelled-headline\">"
                        + "Cancelled <span class=\"nowrap\">May 20, 2026</span></span>"
                        + "<span class=\"flight-cancelled-reason\">Credit on file</span></span>")
                .contains("<span class=\"flight-action-disabled\" title=\"This flight was cancelled\">Edit</span>")
                .contains("<span class=\"flight-action-disabled\" title=\"This flight was cancelled\">Cancel</span>")
                .as("nothing left to follow on a cancelled flight")
                .doesNotContain("href=\"/booked-flights/" + flightId.id());
    }

    @Test
    void theCancellationDateIsReadInTheDepartureAirportsZone() {
        // 03:00 UTC on the 21st is still the evening of the 20th in Los Angeles.
        BookedFlightView cancelled = new BookedFlightView(FlightId.random(), "United", "UA59", "SFO→FRA",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 6, 13, 55), ZoneId.of("America/Los_Angeles")),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 7, 9, 45), ZoneId.of("Europe/Berlin")),
                List.of(new ChangeEntry(LocalDateTime.of(2026, 5, 1, 12, 0), "Booked on 2026-05-01 12:00PM")),
                true, "", Instant.parse("2026-05-21T03:00:00Z"));

        String html = BookedFlightsRenderer.render(List.of(cancelled), TimeView.ALL, CancelledView.SHOW, 1);

        assertThat(html)
                .contains("<span class=\"flight-cancelled-badge\"><span class=\"flight-cancelled-headline\">"
                        + "Cancelled <span class=\"nowrap\">May 20, 2026</span></span></span>")
                .as("no reason given, so no empty reason line")
                .doesNotContain("<span class=\"flight-cancelled-reason\">");
    }

    /**
     * The layout rules the 2026-09-29 measurements settled, pinned as CSS because the renderer is
     * the only place they exist. What they buy — no column wraps while another has room, no
     * overflow at any width — was measured in headless Chrome, which this tier cannot do; these
     * assertions stop the declarations that produce it being "tidied" away.
     */
    @Test
    void theGridLetsRouteAbsorbSpaceOnlyAfterEveryOtherColumnIsOnOneLine() {
        // A row, not an empty list: the empty state renders no header, and the header is asserted.
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges("Sat, Jun 6, 1:55 PM", "SFO→FRA", "United", "UA59")
        ), TimeView.FUTURE);

        assertThat(html)
                .as("auto tracks reach max-content before the one fr track gets anything")
                .contains("grid-template-columns: auto auto 1fr auto auto 28px auto;")
                .doesNotContain("grid-template-columns: 2fr 2fr")
                .doesNotContain("min-content min-content")
                .as("the cancelled box claims Route's width by content, and only on a table with room")
                .contains("container-type: inline-size;")
                .contains("@container (width >= 41rem) {\n    .flight-cancelled-headline { white-space: nowrap; }")
                .as("the actions never wrap, so toggling the cancelled view cannot move them")
                .contains(".flight-actions { display: flex; gap: 0.75rem; align-items: baseline; }")
                .doesNotContain(".flight-actions { display: flex; flex-wrap: wrap;")
                .as("stack where the floors stop fitting, measured")
                .contains("@media (max-width: 640px) {")
                .as("no side insets: they were width taken from the table, and pushed its floors past the breakpoint")
                .contains(".conference-container { margin: 1rem 0 0; }")
                .as("the time always on its own line under the date, not only when the column is narrow")
                .contains(".flight-card-row time > .nowrap { display: block; }")
                .as("a short header, so it is not the Flight # column's widest line")
                .contains("<div>Flight #</div>")
                .doesNotContain("<div>Flight Number</div>");
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
    void aTripLegShowsItsCodeAndItsPlaceInTheBookingUnderTheFlightNumber() {
        FlightId flightId = FlightId.random();
        String html = renderWithTrips(List.of(viewWithoutChanges(flightId, "", "YOW→ORD", "United", "UA3510")),
                Map.of(flightId, trip("K3PQ9R", 3, 4, 0, 1)));

        assertThat(html)
                .contains("<span class=\"flight-trip-code\">K3PQ9R</span>")
                .contains("<span class=\"flight-trip-leg\">leg 3 of 4</span>");
    }

    @Test
    void aSingleFlightBookingSaysOneWayRatherThanLegOneOfOne() {
        FlightId flightId = FlightId.random();
        String html = renderWithTrips(List.of(viewWithoutChanges(flightId, "", "HAM→MUC", "Lufthansa", "LH2093")),
                Map.of(flightId, trip("P5C9DV", 1, 1, 0, 1)));

        assertThat(html)
                .contains("<span class=\"flight-trip-leg\">one-way</span>")
                .doesNotContain("leg 1 of 1");
    }

    @Test
    void eachTripRowWearsItsTripsHueAndAnUntrippedRowWearsNone() {
        FlightId outer = FlightId.random();
        FlightId inner = FlightId.random();
        FlightId byHand = FlightId.random();
        String html = renderWithTrips(List.of(
                        viewWithoutChanges(outer, "", "SFO→ORD", "United", "UA1"),
                        viewWithoutChanges(inner, "", "YOW→YYZ", "Air Canada", "AC8201"),
                        viewWithoutChanges(byHand, "", "SFO→LAX", "United", "UA608")),
                Map.of(outer, trip("MD7LKB", 1, 4, 0, 0), inner, trip("QX4TZN", 1, 2, 0, 1)));

        assertThat(html)
                .contains("<div class=\"flight-card flight-card-row flight-card--trip-0\">")
                .contains("<div class=\"flight-card flight-card-row flight-card--trip-1\">")
                .contains("<div class=\"flight-card flight-card-row\">");
    }

    @Test
    void aTripThatCanBeCancelledWholeLinksToItsCancelPageInRed() {
        FlightItineraryId itinerary = FlightItineraryId.of(UUID.randomUUID());
        FlightId flightId = FlightId.random();
        String html = renderWithTrips(List.of(viewWithoutChanges(flightId, "", "SFO→ORD", "United", "UA1")),
                Map.of(flightId, new FlightTrip(itinerary, "MD7LKB", 1, 2, 0, 0)));

        assertThat(html)
                .contains("<a class=\"flight-trip-cancel-link\" href=\"/booked-itineraries/"
                          + itinerary.id() + "/cancel\">Cancel trip</a>")
                .contains(".flight-trip-cancel-link { font-size: 0.85rem; color: #b00;");
    }

    @Test
    void aTripWithADepartedLegShowsCancelTripDisabledWithTheReasonWrittenOut() {
        FlightId flightId = FlightId.random();
        String html = renderWithTrips(List.of(viewWithoutChanges(flightId, "", "YOW→ORD", "United", "UA3510")),
                Map.of(flightId, trip("K3PQ9R", 3, 4, 2, 0)));

        assertThat(html)
                .contains("<span class=\"flight-action-disabled\">Cancel trip</span>")
                .as("the reason is text, because the iPad has no hover for a tooltip")
                .contains("<span class=\"flight-trip-why\">2 legs already left</span>")
                .doesNotContain("href=\"/booked-itineraries/");
    }

    @Test
    void oneDepartedLegIsSaidInTheSingular() {
        FlightId flightId = FlightId.random();
        String html = renderWithTrips(List.of(viewWithoutChanges(flightId, "", "YOW→ORD", "United", "UA3510")),
                Map.of(flightId, trip("K3PQ9R", 2, 2, 1, 0)));

        assertThat(html)
                .contains("<span class=\"flight-trip-why\">1 leg already left</span>");
    }

    @Test
    void aCancelledFlightsTripActionIsDisabledAndSaysWhy() {
        FlightId flightId = FlightId.random();
        String html = renderWithTrips(List.of(cancelledView(flightId)),
                Map.of(flightId, trip("MD7LKB", 1, 2, 0, 0)));

        assertThat(html)
                .contains("<span class=\"flight-trip-why\">Flight cancelled</span>")
                .doesNotContain("href=\"/booked-itineraries/");
    }

    @Test
    void aHandEnteredFlightKeepsAnEmptyTripLineWhenAnotherRowHasATripSoActionsNeverMove() {
        FlightId inTrip = FlightId.random();
        FlightId byHand = FlightId.random();
        String html = renderWithTrips(List.of(
                        viewWithoutChanges(inTrip, "", "SFO→ORD", "United", "UA1"),
                        viewWithoutChanges(byHand, "", "SFO→LAX", "United", "UA608")),
                Map.of(inTrip, trip("MD7LKB", 1, 2, 0, 0)));

        assertThat(html)
                .contains("<span class=\"flight-trip-slot\" aria-hidden=\"true\">&nbsp;</span>");
    }

    @Test
    void aPageWithNoTripsReservesNoTripLineAtAll() {
        String html = BookedFlightsRenderer.render(List.of(
                viewWithoutChanges("", "SFO→LAX", "United", "UA608")), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("class=\"flight-trip-slot\"")
                .doesNotContain("Cancel trip");
    }

    @Test
    void theTripHuesAreIndigoAndTealNeverAmber() {
        String html = renderWithTrips(List.of(), Map.of());

        assertThat(html)
                .contains(".flight-card--trip-0 { --trip-edge: #4f46e5; --trip-soft: #e0e7ff; }")
                .contains(".flight-card--trip-1 { --trip-edge: #0f8b8d; --trip-soft: #d5f1f1; }");
    }

    @Test
    void bookAnotherFlightLinkIsPresent() {
        String html = BookedFlightsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html).contains("/book-flight");
    }

    private static String renderWithTrips(List<BookedFlightView> flights, Map<FlightId, FlightTrip> trips) {
        return BookedFlightsRenderer.render(flights, TimeView.ALL, CancelledView.SHOW, 0, trips);
    }

    private static FlightTrip trip(String code, int leg, int of, int departed, int hue) {
        return new FlightTrip(FlightItineraryId.of(UUID.randomUUID()), code, leg, of, departed, hue);
    }

    private static BookedFlightView cancelledView(FlightId flightId) {
        return new BookedFlightView(flightId, "United", "UA1", "SFO→ORD",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 6, 13, 55), UTC),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 7, 9, 45), UTC),
                List.of(new ChangeEntry(LocalDateTime.of(2026, 5, 1, 12, 0), "Booked on 2026-05-01 12:00PM")),
                true, "", Instant.parse("2026-06-01T00:00:00Z"));
    }

    private static BookedFlightView viewWithoutChanges(String display, String route,
                                                       String airline, String flightNumber) {
        return viewWithoutChanges(FlightId.random(), display, route, airline, flightNumber);
    }

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static BookedFlightView cancelledView(FlightId flightId, Instant cancelledOn, String reason) {
        return new BookedFlightView(
                flightId, "United", "UA59", "SFO→FRA",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 6, 13, 55), UTC),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 7, 9, 45), UTC),
                List.of(new ChangeEntry(LocalDateTime.of(2026, 5, 1, 12, 0), "Booked on 2026-05-01 12:00PM")),
                true, reason, cancelledOn);
    }

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
