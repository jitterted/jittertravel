package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarViewBuilderTest {

    // "today" pinned far before every range below, so existing assertions see
    // neither is-past nor is-today markup. Past/today behavior has dedicated tests.
    private static final LocalDate TODAY = LocalDate.of(2020, 1, 1);

    @Test
    void emptyRangeRendersDayLabelsAndNoLaneRows() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5),
                TODAY,
                false
        );

        // No entries means every week collapses to its day-label row only.
        assertThat(html).contains("day-label-cell");
        assertThat(html).doesNotContain("class=\"entry");
        // Collapsed weeks use only the auto header row.
        assertThat(html).contains("grid-template-rows: auto;");
    }

    @Test
    void monthStartCellsGetIsMonthStartClassOnDayLabelCell() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5),
                TODAY,
                false
        );

        // First visible cell (Sunday May 24, 2026) and June 1 are month-starts.
        assertThat(html).contains(">May 24, 2026<");
        assertThat(html).contains(">Jun 1<");
        // The is-month-start L-border applies to day-label cells now.
        assertThat(html).contains("day-label-cell month-tint-odd is-month-start");
    }

    @Test
    void januaryMonthStartIncludesYearOnDayLabelCell() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2027, 1, 5),
                TODAY,
                false
        );

        assertThat(html).contains(">Jan 1, 2027<");
    }

    @Test
    void conferenceEntryRendersWithTitleAndLocation() {
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 4, 17, 0),
                "DevConf",
                List.of("(Portland, USA)"),
                "DevConf cont'd",
                List.of("(Portland, USA)"),
                null
        );

        String html = CalendarViewBuilder.render(
                List.of(conf),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5),
                TODAY,
                false
        );

        assertThat(html).contains("entry entry--conference");
        assertThat(html).contains(">DevConf<");
        assertThat(html).contains(">(Portland, USA)<");
        // The conference week now has 1 lane sub-row.
        assertThat(html).contains("grid-template-rows: auto repeat(1, auto);");
    }

    @Test
    void flightAndConferenceAcrossWeekBoundaryRenderInSeparateLanes() {
        // Flight UA59: SFO->FRA, departs Sat 2026-06-06 13:55, arrives Sun 2026-06-07 09:45.
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT,
                LocalDateTime.of(2026, 6, 6, 13, 55),
                LocalDateTime.of(2026, 6, 7, 9, 45),
                "✈️ SFO\u2192FRA",
                List.of("Departs 1:55 PM"),
                null,
                List.of("Arr 9:45 AM"),
                null
        );
        // Conference DDD Europe 2026: Sun 2026-06-07 11:00 -> Wed 2026-06-10 17:00.
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 7, 11, 0),
                LocalDateTime.of(2026, 6, 10, 17, 0),
                "DDD Europe 2026",
                List.of("(Frankfurt, Germany)"),
                "DDD Europe 2026 cont'd",
                List.of("(Frankfurt, Germany)"),
                null
        );

        String html = CalendarViewBuilder.render(
                List.of(conf, flight),
                LocalDate.of(2026, 6, 6),
                LocalDate.of(2026, 6, 10),
                TODAY,
                false
        );

        // Both entry titles appear (flight title only on departure segment).
        assertThat(html).contains(">✈\uFE0F SFO\u2192FRA<");
        assertThat(html).contains(">Departs 1:55 PM<");
        assertThat(html).contains(">DDD Europe 2026<");
        assertThat(html).contains(">(Frankfurt, Germany)<");
        // The continuation flight segment shows ONLY the arrival subtitle, no title.
        assertThat(html).contains(">Arr 9:45 AM<");
        long titleCount = html.split(">✈\uFE0F SFO\u2192FRA<", -1).length - 1;
        assertThat(titleCount).isEqualTo(1);

        // Both kinds of entry cells are present.
        assertThat(html).contains("entry entry--flight");
        assertThat(html).contains("entry entry--conference");
        // The continuation segment is marked as such.
        assertThat(html).contains("entry--continuation");
    }

    @Test
    void overlappingEntriesInSameLaneStackIntoSubRows() {
        CalendarEntry a = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 4, 17, 0),
                "ConfA", List.of("(City, Country)"),
                "ConfA cont'd", List.of("(City, Country)"),
                null
        );
        CalendarEntry b = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 5, 17, 0),
                "ConfB", List.of("(City, Country)"),
                "ConfB cont'd", List.of("(City, Country)"),
                null
        );

        String html = CalendarViewBuilder.render(
                List.of(a, b),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 6),
                TODAY,
                false
        );

        // Two overlapping conferences -> 2 sub-rows in the conference lane.
        assertThat(html).contains("grid-template-rows: auto repeat(2, auto);");
        assertThat(html).contains(">ConfA<");
        assertThat(html).contains(">ConfB<");
    }

    @Test
    void lodgingEntryWithMapsUrlRendersTitleAsLink() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING,
                LocalDateTime.of(2026, 6, 10, 15, 0),
                LocalDateTime.of(2026, 6, 12, 11, 0),
                "Grand Hotel Berlin",
                List.of("Berlin, DE"),
                "Grand Hotel Berlin cont'd",
                List.of("Berlin, DE"),
                "https://maps.google.com/?q=Grand+Hotel+Berlin"
        );

        String html = CalendarViewBuilder.render(
                List.of(hotel),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false
        );

        assertThat(html)
                .contains("href=\"https://maps.google.com/?q=Grand+Hotel+Berlin\"")
                .contains(">Grand Hotel Berlin<");
    }

    @Test
    void fixedLaneOrderingPlacesConferencesAboveFlights() {
        // Both occupy the same week so we have two lanes stacked.
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 8, 9, 0),
                LocalDateTime.of(2026, 6, 8, 17, 0),
                "Conf", List.of("(City, Country)"),
                "Conf cont'd", List.of("(City, Country)"),
                null
        );
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT,
                LocalDateTime.of(2026, 6, 9, 9, 0),
                LocalDateTime.of(2026, 6, 9, 13, 0),
                "✈️ A→B", List.of("Departs 9:00 AM"),
                null, List.of("Arr 1:00 PM"),
                null
        );

        String html = CalendarViewBuilder.render(
                List.of(flight, conf),  // intentionally out of lane order
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 13),
                TODAY,
                false
        );

        // Two lane sub-rows.
        assertThat(html).contains("grid-template-rows: auto repeat(2, auto);");
        // Conference must be on grid-row 2 (first lane), flight on grid-row 3 (second lane).
        int confIndex = html.indexOf("grid-row: 2;");
        int flightIndex = html.indexOf("grid-row: 3;");
        assertThat(confIndex).isPositive();
        assertThat(flightIndex).isPositive();
        // And the conference entry actually sits on row 2.
        int confTitle = html.indexOf(">Conf<");
        int row2 = html.lastIndexOf("grid-row: 2;", confTitle);
        assertThat(row2).isPositive();
    }

    @Test
    void daysBeforeTodayGetIsPastClassAndTodayGetsIsTodayClass() {
        // Week of Sun 2026-06-07 .. Sat 2026-06-13; pin today to Wed 2026-06-10.
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 13),
                LocalDate.of(2026, 6, 10),
                false
        );

        // June 9 (the day before today) is past; its label cell is hatched.
        assertThat(html)
                .contains(">9<")
                .contains("is-past");
        // June 10 (today) gets the accent-column class, and is not also marked past.
        assertThat(html).contains("is-today");
        // Today's own label cell must not carry is-past.
        int todayLabel = html.indexOf(">10<");
        int cellStart = html.lastIndexOf("day-label-cell", todayLabel);
        String todayCellTag = html.substring(cellStart, todayLabel);
        assertThat(todayCellTag)
                .contains("is-today")
                .doesNotContain("is-past");
    }

    @Test
    void allFutureDaysHaveNeitherPastNorTodayClass() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 13),
                LocalDate.of(2020, 1, 1),
                false
        );

        assertThat(html)
                .doesNotContain("is-past")
                .doesNotContain("is-today");
    }

    @Test
    void weeksBeforeCurrentWeekAreMarkedCollapsedAndKeepEntryMarkup() {
        // Conference Mon-Wed 2026-06-01..03; today is Mon 2026-06-15, so its week
        // (Sun 2026-06-14..) is current and the conference week is a prior week.
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 3, 17, 0),
                "PastConf", List.of("(City, Country)"),
                "PastConf cont'd", List.of("(City, Country)"),
                null
        );

        String html = CalendarViewBuilder.render(
                List.of(conf),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 15),
                false
        );

        // The prior week carries the collapsed marker, but its entry markup is retained
        // (hidden by CSS) so a click can reveal it.
        assertThat(html).contains("calendar-week--collapsed");
        assertThat(html).contains(">PastConf<");
        // Per-day badges show the count on each day the entry spans (Mon/Tue/Wed).
        long badgeCount = html.split("class=\"day-badge\"", -1).length - 1;
        assertThat(badgeCount).isEqualTo(3);
        // A global toggle is offered because a collapsed week has entries.
        assertThat(html).contains("id=\"toggle-all-weeks\"");
    }

    @Test
    void currentAndFutureWeeksAreNotCollapsedAndHaveNoBadges() {
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 16, 17, 0),
                "FutureConf", List.of("(City, Country)"),
                "FutureConf cont'd", List.of("(City, Country)"),
                null
        );

        String html = CalendarViewBuilder.render(
                List.of(conf),
                LocalDate.of(2026, 6, 14),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 15),
                false
        );

        assertThat(html).doesNotContain("calendar-week--collapsed");
        assertThat(html).doesNotContain("class=\"day-badge\"");
        assertThat(html).doesNotContain("id=\"toggle-all-weeks\"");
    }

    @Test
    void gatheringEntryRendersWithGatheringCssClass() {
        CalendarEntry gathering = new CalendarEntry(
                EntryKind.GATHERING,
                LocalDateTime.of(2026, 6, 10, 18, 0),
                LocalDateTime.of(2026, 6, 10, 21, 0),
                "London Java Community",
                List.of("Skills Matter", "London, GB"),
                null,
                null,
                "https://meetup.com/ljc/events/123"
        );

        String html = CalendarViewBuilder.render(
                List.of(gathering),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false
        );

        assertThat(html).contains("entry entry--gathering");
        assertThat(html).contains(">London Java Community<");
        assertThat(html).contains(">Skills Matter<");
        assertThat(html).contains(">London, GB<");
        assertThat(html).contains("href=\"https://meetup.com/ljc/events/123\"");
    }
}
