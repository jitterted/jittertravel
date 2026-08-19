package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import dev.ted.jittertravel.application.SubtitleLine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
    void everyGridPinsItsColumnsToMinmaxZeroSoDaysAlignAcrossWeeks() {
        // Each week is its own grid, so a bare 1fr (= minmax(auto, 1fr)) lets one wide entry
        // widen that week's column alone and knock it out of registration with the header and
        // the other weeks. Alignment holds only while every grid's tracks are content-independent.
        String html = ConfirmedCalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false);

        assertThat(html)
                .contains("grid-template-columns: repeat(7, minmax(0, 1fr))")
                .doesNotContain("grid-template-columns: repeat(7, 1fr)");
    }

    @Test
    void narrowScreensDropTheOuterSideMarginSoTheSevenDaysGetTheWidth() {
        // On a phone the 4rem gutters take 8rem of ~390px — more than a day column. The
        // media query hands that back to the grid; it is the only thing making the calendar
        // usable at that width, so losing it silently would go unnoticed until a device test.
        String html = ConfirmedCalendarRenderer.render(List.of(), LocalDate.of(2026, 6, 11), false);

        assertThat(html)
                .contains("@media (max-width: 900px)")
                .contains(".calendar-outer { margin: 1rem 0; }");
    }

    @Test
    void publicUserSeesRedactedHotelName() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING,
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                "Grand Hotel", lines("Berlin, Germany"),
                "Grand Hotel cont'd", lines("Berlin, Germany"),
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
                "🚄 London → Manchester", lines("9:00 AM → 1:00 PM"),
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
                "🚄 London → Manchester", lines("9:00 AM → 1:00 PM"),
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
                "✈️ SFO→JFK", lines("9:00 AM → 1:00 PM"),
                null, null, null, "/booked-flights/flight-123"
        );

        String html = ConfirmedCalendarRenderer.render(List.of(flight), LocalDate.of(2026, 6, 11), false, true);

        assertThat(html).contains("href=\"/booked-flights/flight-123\"");
    }

    @Test
    void reversedExplicitDateRangeIsNormalizedToForwardOrder() {
        LocalDate today = LocalDate.of(2026, 6, 11);
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);

        String forward = ConfirmedCalendarRenderer.render(List.of(), today, false, false, from, to);
        String reversed = ConfirmedCalendarRenderer.render(List.of(), today, false, false, to, from);

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

        String html = ConfirmedCalendarRenderer.render(entries, today, false, false,
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
        String html = ConfirmedCalendarRenderer.render(List.of(), today, false, false,
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

        String html = ConfirmedCalendarRenderer.render(entries, today, false, false,
                                                       LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(html).contains("SpansIntoRange");
    }

    @Test
    void entryOverlappingTheEndOfTheDateRangeIsRendered() {
        LocalDate today = LocalDate.of(2026, 6, 11);
        List<CalendarEntry> entries = List.of(
                conference("SpansOutOfRange", LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 4))
        );

        String html = ConfirmedCalendarRenderer.render(entries, today, false, false,
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

        String html = ConfirmedCalendarRenderer.render(entries, today, false, false,
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

        String html = ConfirmedCalendarRenderer.render(entries, today, false, false,
                                                       null, LocalDate.of(2026, 7, 31));

        assertThat(html)
                .contains("BeforeTo")
                .doesNotContain("AfterTo");
    }

    @Test
    void defaultRangeStartsAtTheSundayOfTheWeekBeforeToday() {
        LocalDate today = LocalDate.of(2026, 6, 11); // Thursday
        // One week before today is 2026-06-04, whose grid week starts Sunday 2026-05-31.

        String html = ConfirmedCalendarRenderer.render(List.of(), today, false);

        assertThat(html)
                .as("default window opens at the week containing today minus one week")
                .contains("/itinerary?date=2026-05-31")
                .doesNotContain("/itinerary?date=2026-05-30")
                // ...and specifically one week back, not two (which would open Sunday 2026-05-24).
                .doesNotContain("/itinerary?date=2026-05-24");
    }

    private static CalendarEntry conference(String title, LocalDate start, LocalDate end) {
        return new CalendarEntry(
                EntryKind.CONFERENCE,
                start.atTime(9, 0),
                end.atTime(17, 0),
                title, lines("subtitle for " + title),
                title + " cont'd", lines("continued subtitle for " + title),
                null
        );
    }

    @Test
    void authenticatedUserSeesFullHotelName() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING,
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                "Grand Hotel", lines("Berlin, Germany"),
                "Grand Hotel cont'd", lines("Berlin, Germany"),
                "https://maps.google.com/grand"
        );

        String html = ConfirmedCalendarRenderer.render(List.of(hotel), LocalDate.of(2026, 6, 11), false);

        assertThat(html).contains("Grand Hotel");
    }

    private static List<SubtitleLine> lines(String... values) {
        return Arrays.stream(values).<SubtitleLine>map(SubtitleLine.Text::new).toList();
    }
}
