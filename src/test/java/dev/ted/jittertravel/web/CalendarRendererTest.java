package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.application.ZoneDisplay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarRendererTest {

    @Test
    void emptyEntriesRendersCalendarPage() {
        // Assert whole elements, not bare words. contains("Calendar") passed against a page
        // titled "Confirmed Calendar" — and would pass against the nav's own Calendar link even
        // with no <title> at all, so it asserted nothing about the title it was meant to pin.
        String html = CalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false);

        assertThat(html)
                .as("the document title is exactly Calendar")
                .contains("<title>Calendar</title>")
                .as("and the nav still offers the way home")
                .contains("<a href=\"/\">JitterTravel</a>");
    }

    /**
     * The ground-transfer lane gets a colour of its own — reason 2 for giving it its own
     * {@code EntryKind} rather than reusing TRAIN, which would have rendered a taxi in the train's
     * orange. The class name comes from {@code EntryKind.name().toLowerCase()}, underscore and all,
     * so the rule must be spelled {@code entry--ground_transfer} or it silently matches nothing.
     */
    @Test
    void theGroundTransferLaneHasItsOwnColourRuleNotTheTrainOne() {
        String html = CalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false);

        assertThat(html)
                .contains("--entry-ground_transfer-bg: #fef9c3; --entry-ground_transfer-fg: #854d0e;")
                .contains(".entry--ground_transfer { background-color: var(--entry-ground_transfer-bg); "
                          + "color: var(--entry-ground_transfer-fg); }")
                .as("a hyphenated spelling would never match the generated class")
                .doesNotContain(".entry--ground-transfer {");
    }

    @Test
    void everyGridPinsItsColumnsToMinmaxZeroSoDaysAlignAcrossWeeks() {
        // Each week is its own grid, so a bare 1fr (= minmax(auto, 1fr)) lets one wide entry
        // widen that week's column alone and knock it out of registration with the header and
        // the other weeks. Alignment holds only while every grid's tracks are content-independent.
        String html = CalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false);

        assertThat(html)
                .contains("grid-template-columns: repeat(7, minmax(0, 1fr))")
                .doesNotContain("grid-template-columns: repeat(7, 1fr)");
    }

    @Test
    void theAwayBandIsABottomBorderOnly() {
        // The band is one tokenized bottom border and nothing else: no departure/return caps.
        // Side borders would eat a cell's content box and fight the amber month-start border for
        // the left edge on equal specificity, where source order alone would decide the winner.
        String html = CalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false);

        assertThat(html)
                .contains("--calendar-away-color: turquoise;")
                .contains("--calendar-away-border-width: 4px;")
                .contains("border-bottom: var(--calendar-away-border-width) solid var(--calendar-away-color);")
                .doesNotContain("border-bottom: 4px solid turquoise")
                .doesNotContain("border-left: var(--calendar-away-border-width)")
                .doesNotContain("border-right: var(--calendar-away-border-width)");
    }

    @Test
    void awayDaysReachTheDayLabelCellsOfTheRenderedGrid() {
        String html = CalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false, false,
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14), ZoneDisplay.entryOnly(),
                Set.of(LocalDate.of(2026, 6, 12)));

        assertThat(html)
                .contains("is-away\"><a href=\"/itinerary?date=2026-06-12\"")
                .doesNotContain("is-away\"><a href=\"/itinerary?date=2026-06-13\"");
    }

    @Test
    void narrowScreensDropTheOuterSideMarginSoTheSevenDaysGetTheWidth() {
        // On a phone the 4rem gutters take 8rem of ~390px — more than a day column. The
        // media query hands that back to the grid; it is the only thing making the calendar
        // usable at that width, so losing it silently would go unnoticed until a device test.
        String html = CalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false);

        assertThat(html)
                .contains("@media (max-width: 900px)")
                .contains(".calendar-outer { margin: 1rem 0; }");
    }

    @Test
    void publicUserSeesRedactedHotelName() {
        CalendarEntry hotel = new CalendarEntry(
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                "Grand Hotel", lines("Berlin, Germany"),
                "Grand Hotel cont'd", lines("Berlin, Germany"),
                new EntryDetails.Lodging("https://maps.google.com/grand", null)
        );

        String html = CalendarRenderer.render(List.of(hotel), LocalDate.of(2026, 6, 11), true);

        assertThat(html)
                .contains("Hotel")
                .doesNotContain("Grand Hotel");
    }

    @Test
    void ownerSeesEditLinkOnTrainEntry() {
        CalendarEntry train = new CalendarEntry(
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 13, 0),
                "🚄 London → Manchester", lines("9:00 AM → 1:00 PM"),
                new EntryDetails.Train("/booked-trains/trip-123")
        );

        String html = CalendarRenderer.render(List.of(train), LocalDate.of(2026, 6, 11), false, true);

        assertThat(html).contains("href=\"/booked-trains/trip-123\"");
    }

    @Test
    void nonOwnerSeesNoEditLinkOnTrainEntry() {
        CalendarEntry train = new CalendarEntry(
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 13, 0),
                "🚄 London → Manchester", lines("9:00 AM → 1:00 PM"),
                new EntryDetails.Train("/booked-trains/trip-123")
        );

        String html = CalendarRenderer.render(List.of(train), LocalDate.of(2026, 6, 11), false, false);

        assertThat(html).doesNotContain("href=\"/booked-trains/");
    }

    @Test
    void ownerSeesEditLinkOnFlightEntry() {
        CalendarEntry flight = new CalendarEntry(
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 13, 0),
                "✈️ SFO→JFK", lines("9:00 AM → 1:00 PM"),
                new EntryDetails.Flight("/booked-flights/flight-123")
        );

        String html = CalendarRenderer.render(List.of(flight), LocalDate.of(2026, 6, 11), false, true);

        assertThat(html).contains("href=\"/booked-flights/flight-123\"");
    }

    @Test
    void reversedExplicitDateRangeIsNormalizedToForwardOrder() {
        LocalDate today = LocalDate.of(2026, 6, 11);
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);

        String forward = CalendarRenderer.render(List.of(), today, false, false, from, to);
        String reversed = CalendarRenderer.render(List.of(), today, false, false, to, from);

        assertThat(reversed)
                .as("Reversed from/to must render the same (non-empty) window as forward order")
                .isEqualTo(forward)
                .contains("Aug 1");
    }

    @Test
    void explicitDateRangeRendersOnlyEntriesWithinThatRange() {
        LocalDate today = LocalDate.of(2026, 6, 11);
        List<CalendarEntry> entries = List.of(
                conference("WayBeforeRange", LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 8)),
                conference("JustBeforeRange", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17)),
                conference("InsideRangeEarly", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)),
                conference("InsideRangeLate", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 29)),
                conference("JustAfterRange", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)),
                conference("WayAfterRange", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7))
        );

        String html = CalendarRenderer.render(entries, today, false, false,
                                                       LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(html)
                .contains("InsideRangeEarly")
                .contains("InsideRangeLate")
                .doesNotContain("WayBeforeRange")
                .doesNotContain("JustBeforeRange")
                .doesNotContain("JustAfterRange")
                .doesNotContain("WayAfterRange");
    }

    @Test
    void explicitDateRangeRendersOnlyDaysWithinTheWeeksCoveringThatRange() {
        LocalDate today = LocalDate.of(2026, 6, 11);

        // from = Wed 2026-07-01 -> grid starts Sun 2026-06-28
        // to   = Fri 2026-07-31 -> grid ends   Sat 2026-08-01
        String html = CalendarRenderer.render(List.of(), today, false, false,
                                                       LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(html)
                .contains("/itinerary?date=2026-06-28")
                .contains("/itinerary?date=2026-07-15")
                .contains("/itinerary?date=2026-08-01")
                .doesNotContain("/itinerary?date=2026-06-27")
                .doesNotContain("/itinerary?date=2026-08-02");
    }

    @Test
    void entryOverlappingTheStartOfTheDateRangeIsRendered() {
        LocalDate today = LocalDate.of(2026, 6, 11);
        List<CalendarEntry> entries = List.of(
                conference("SpansIntoRange", LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 3))
        );

        String html = CalendarRenderer.render(entries, today, false, false,
                                                       LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(html).contains("SpansIntoRange");
    }

    @Test
    void entryOverlappingTheEndOfTheDateRangeIsRendered() {
        LocalDate today = LocalDate.of(2026, 6, 11);
        List<CalendarEntry> entries = List.of(
                conference("SpansOutOfRange", LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 4))
        );

        String html = CalendarRenderer.render(entries, today, false, false,
                                                       LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(html).contains("SpansOutOfRange");
    }

    @Test
    void onlyFromGivenRendersFromThatDateThroughLastEntry() {
        LocalDate today = LocalDate.of(2026, 6, 11);
        List<CalendarEntry> entries = List.of(
                conference("BeforeFrom", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 6)),
                conference("AfterFrom", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8))
        );

        String html = CalendarRenderer.render(entries, today, false, false,
                                                       LocalDate.of(2026, 7, 1), null);

        assertThat(html)
                .contains("AfterFrom")
                .doesNotContain("BeforeFrom");
    }

    @Test
    void onlyToGivenRendersFirstEntryThroughThatDate() {
        LocalDate today = LocalDate.of(2026, 6, 11);
        List<CalendarEntry> entries = List.of(
                conference("BeforeTo", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)),
                conference("AfterTo", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16))
        );

        String html = CalendarRenderer.render(entries, today, false, false,
                                                       null, LocalDate.of(2026, 7, 31));

        assertThat(html)
                .contains("BeforeTo")
                .doesNotContain("AfterTo");
    }

    @Test
    void defaultRangeStartsAtTheSundayOfTheWeekBeforeToday() {
        LocalDate today = LocalDate.of(2026, 6, 11); // Thursday
        // One week before today is 2026-06-04, whose grid week starts Sunday 2026-05-31.

        String html = CalendarRenderer.render(List.of(), today, false);

        assertThat(html)
                .as("default window opens at the week containing today minus one week")
                .contains("/itinerary?date=2026-05-31")
                .doesNotContain("/itinerary?date=2026-05-30")
                // ...and specifically one week back, not two (which would open Sunday 2026-05-24).
                .doesNotContain("/itinerary?date=2026-05-24");
    }

    private static CalendarEntry conference(String title, LocalDate start, LocalDate end) {
        return new CalendarEntry(
                start.atTime(9, 0),
                end.atTime(17, 0),
                title, lines("subtitle for " + title),
                title + " cont'd", lines("continued subtitle for " + title),
                new EntryDetails.Conference(null)
        );
    }

    @Test
    void authenticatedUserSeesFullHotelName() {
        CalendarEntry hotel = new CalendarEntry(
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                "Grand Hotel", lines("Berlin, Germany"),
                "Grand Hotel cont'd", lines("Berlin, Germany"),
                new EntryDetails.Lodging("https://maps.google.com/grand", null)
        );

        String html = CalendarRenderer.render(List.of(hotel), LocalDate.of(2026, 6, 11), false);

        assertThat(html).contains("Grand Hotel");
    }

    private static List<SubtitleLine> lines(String... values) {
        return Arrays.stream(values).<SubtitleLine>map(SubtitleLine.Text::new).toList();
    }
}
