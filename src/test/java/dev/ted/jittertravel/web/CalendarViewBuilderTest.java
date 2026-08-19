package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarViewBuilderTest {

    // "today" pinned far before every range below, so existing assertions see
    // neither is-past nor is-today markup. Past/today behavior has dedicated tests.
    private static final LocalDate TODAY = LocalDate.of(2020, 1, 1);

    @Test
    void emptyNonCollapsedWeekRendersOneEmptyLaneBandForBreathingRoom() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5),
                TODAY,
                false
        );

        // No entries, but these weeks are in the future (TODAY is 2020), so they are not
        // collapsed: each gets a single empty lane band so the week reads as open space.
        assertThat(html).contains("day-label-cell");
        assertThat(html).doesNotContain("class=\"entry");
        assertThat(html).contains("lane-cell--empty");
        assertThat(html).contains("grid-template-rows: auto repeat(1, auto);");
    }

    @Test
    void collapsedEmptyPastWeeksStayAsDayLabelRowOnly() {
        // today = Mon 2026-06-15; the weeks before its week are empty and collapse to the
        // day-label row only (no empty band — that is reserved for non-collapsed weeks).
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 15),
                false
        );

        assertThat(html).contains("calendar-week--collapsed");
        assertThat(html).contains("grid-template-rows: auto;");
    }

    @Test
    void ownerFutureDayRendersDisclosureMenuWithDatedCreateLinks() {
        // today = Fri 2026-06-19, range = the single day Sat 2026-06-20. The grid still
        // expands to the whole week (Sun 14 .. Sat 20), but June 20 is the *only* strictly-
        // future day in it, so exactly one menu renders. That makes each dated assertion
        // strict: the date can only come from June 20's cell, so a one-day arithmetic slip
        // in the link date has nowhere else to surface the expected value from.
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 19),
                false,
                true
        );

        assertThat(html)
                .containsOnlyOnce("<details class=\"day-menu\"")
                .contains("href=\"/itinerary?date=2026-06-20\"")
                .contains("href=\"/book-flight?date=2026-06-20\"")
                .contains("href=\"/book-train?date=2026-06-20\"")
                .contains("href=\"/book-hotel?date=2026-06-20\"")
                .contains("href=\"/plan-gathering?date=2026-06-20\"")
                .contains("href=\"/plan-conference?date=2026-06-20\"");
    }

    @Test
    void ownerTodayAndPastDaysKeepPlainItineraryLinkWithoutMenu() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 14),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 15),
                false,
                true
        );

        // Today (June 15) is a plain itinerary link, and carries no create links.
        assertThat(html)
                .contains("href=\"/itinerary?date=2026-06-15\"")
                .doesNotContain("book-flight?date=2026-06-15")
                .doesNotContain("plan-gathering?date=2026-06-15");
        // Yesterday (June 14) likewise: a plain link, not a menu.
        assertThat(html).doesNotContain("book-flight?date=2026-06-14");
    }

    @Test
    void familyViewerGetsItineraryLinkButNeverTheMenuOrCreateLinks() {
        // Signed-in but not owner: isPublicUser=false, isOwner=false.
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 16),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 15),
                false,
                false
        );

        assertThat(html)
                .contains("href=\"/itinerary?date=2026-06-17\"")
                .doesNotContain("day-menu")
                .doesNotContain("book-flight")
                .doesNotContain("plan-gathering")
                .doesNotContain("plan-conference");
    }

    @Test
    void anonymousViewerGetsPlainNumberWithNoLinksAtAll() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 16),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 15),
                true,
                false
        );

        assertThat(html)
                .doesNotContain("day-menu")
                .doesNotContain("href=\"/itinerary")
                .doesNotContain("book-flight")
                .doesNotContain("plan-gathering");
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
                lines("(Portland, USA)"),
                "DevConf cont'd",
                lines("(Portland, USA)"),
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
                lines("Departs 1:55 PM"),
                null,
                lines("Arr 9:45 AM"),
                null
        );
        // Conference DDD Europe 2026: Sun 2026-06-07 11:00 -> Wed 2026-06-10 17:00.
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 7, 11, 0),
                LocalDateTime.of(2026, 6, 10, 17, 0),
                "DDD Europe 2026",
                lines("(Frankfurt, Germany)"),
                "DDD Europe 2026 cont'd",
                lines("(Frankfurt, Germany)"),
                null
        );

        String html = CalendarViewBuilder.render(
                List.of(conf, flight),
                LocalDate.of(2026, 6, 6),
                LocalDate.of(2026, 6, 10),
                TODAY,
                false
        );

        // Both entry titles appear (flight title only on departure segment). The route carries a
        // <wbr> between the codes so a narrow column can stack them — see
        // unspacedRouteArrowGetsABreakOpportunitySoTheEntryCanNarrow.
        assertThat(html).contains(">✈\uFE0F SFO\u2192<wbr>FRA<");
        assertThat(html).contains(">Departs 1:55 PM<");
        assertThat(html).contains(">DDD Europe 2026<");
        assertThat(html).contains(">(Frankfurt, Germany)<");
        // The continuation flight segment shows ONLY the arrival subtitle, no title.
        assertThat(html).contains(">Arr 9:45 AM<");
        long titleCount = html.split(">✈\uFE0F SFO\u2192<wbr>FRA<", -1).length - 1;
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
                "ConfA", lines("(City, Country)"),
                "ConfA cont'd", lines("(City, Country)"),
                null
        );
        CalendarEntry b = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 5, 17, 0),
                "ConfB", lines("(City, Country)"),
                "ConfB cont'd", lines("(City, Country)"),
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
                lines("Berlin, DE"),
                "Grand Hotel Berlin cont'd",
                lines("Berlin, DE"),
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
                "Conf", lines("(City, Country)"),
                "Conf cont'd", lines("(City, Country)"),
                null
        );
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT,
                LocalDateTime.of(2026, 6, 9, 9, 0),
                LocalDateTime.of(2026, 6, 9, 13, 0),
                "✈️ A→B", lines("Departs 9:00 AM"),
                null, lines("Arr 1:00 PM"),
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
                "PastConf", lines("(City, Country)"),
                "PastConf cont'd", lines("(City, Country)"),
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
                "FutureConf", lines("(City, Country)"),
                "FutureConf cont'd", lines("(City, Country)"),
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
                lines("Skills Matter", "London, GB"),
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
        assertThat(html)
                .as("a gathering Ted only attends shows no speaking badge")
                .doesNotContain("entry-speaking-badge");
    }

    @Test
    void speakingGatheringRendersSpeakingBadge() {
        CalendarEntry gathering = new CalendarEntry(
                EntryKind.GATHERING,
                LocalDateTime.of(2026, 6, 10, 18, 0),
                LocalDateTime.of(2026, 6, 10, 21, 0),
                "London Java Community",
                lines("Skills Matter", "London, GB"),
                null,
                null,
                "https://meetup.com/ljc/events/123",
                true,
                null
        );

        String html = CalendarViewBuilder.render(
                List.of(gathering),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false
        );

        assertThat(html).contains("class=\"entry-speaking-badge\"");
        assertThat(html).contains(">A Ted Talk<");
    }

    @Test
    void ownerEntryWithEditPathShowsEditPencil() {
        CalendarEntry gathering = gatheringWithEditPath();

        String html = CalendarViewBuilder.render(
                List.of(gathering),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false,   // isPublicUser
                true     // isOwner
        );

        assertThat(html).contains("class=\"edit-pencil\" href=\"/planned-gatherings/g-123\"");
    }

    @Test
    void nonOwnerEntryWithEditPathHasNoEditPencil() {
        CalendarEntry gathering = gatheringWithEditPath();

        String html = CalendarViewBuilder.render(
                List.of(gathering),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false,   // isPublicUser
                false    // isOwner
        );

        assertThat(html)
                .doesNotContain("edit-pencil")
                .doesNotContain("/planned-gatherings/");
    }

    private static CalendarEntry gatheringWithEditPath() {
        return new CalendarEntry(
                EntryKind.GATHERING,
                LocalDateTime.of(2026, 6, 10, 18, 0),
                LocalDateTime.of(2026, 6, 10, 21, 0),
                "London Java Community",
                lines("Skills Matter", "London, GB"),
                null,
                null,
                "https://meetup.com/ljc/events/123",
                false,
                "/planned-gatherings/g-123"
        );
    }

    @Test
    void subtitleTimeRangeRendersAsTimeElementsCarryingTheUtcInstant() {
        // A same-day SFO→LAX hop: 9:00 AM PDT is 16:00Z, 10:30 AM PDT is 17:30Z. The segment
        // still sits in its entry-zone day column; only the rendered time carries the instant.
        ZonedTimestamp departure = zoned(LocalDateTime.of(2026, 6, 10, 9, 0), "America/Los_Angeles");
        ZonedTimestamp arrival = zoned(LocalDateTime.of(2026, 6, 10, 10, 30), "America/Los_Angeles");
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT,
                departure.localDateTime(),
                arrival.localDateTime(),
                "SFO to LAX",
                List.of(new SubtitleLine.Range(departure, arrival)),
                null, null, null
        );

        String html = CalendarViewBuilder.render(
                List.of(flight),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false
        );

        assertThat(html).contains(
                "<time datetime=\"2026-06-10T16:00:00Z\" data-fmt=\"h:mm a\">9:00 AM</time>"
                + " → "
                + "<time datetime=\"2026-06-10T17:30:00Z\" data-fmt=\"h:mm a\">10:30 AM</time>");
    }

    @Test
    void labelledSubtitleTimeRendersAsTimeElementCarryingTheUtcInstant() {
        // The overnight-leg shape: London 10:00 PM BST is 21:00Z.
        ZonedTimestamp departure = zoned(LocalDateTime.of(2026, 6, 10, 22, 0), "Europe/London");
        CalendarEntry train = new CalendarEntry(
                EntryKind.TRAIN,
                departure.localDateTime(),
                departure.localDateTime(),
                "London to Paris",
                List.of(new SubtitleLine.At("Departs", departure)),
                null, null, null
        );

        String html = CalendarViewBuilder.render(
                List.of(train),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false
        );

        assertThat(html).contains(
                "Departs <time datetime=\"2026-06-10T21:00:00Z\" data-fmt=\"h:mm a\">10:00 PM</time>");
    }

    @Test
    void unspacedRouteArrowGetsABreakOpportunitySoTheEntryCanNarrow() {
        // "✈️ SFO→MUC" is one unbreakable run (U+2192 offers no break opportunity), which
        // makes the entry's min-content width the whole route. The <wbr> lets the codes stack
        // in a narrow column; the visible text is unchanged.
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT,
                LocalDateTime.of(2026, 6, 10, 9, 0),
                LocalDateTime.of(2026, 6, 10, 13, 0),
                "✈️ SFO→MUC",
                lines("9:00 AM"),
                null, null, null
        );

        String html = CalendarViewBuilder.render(
                List.of(flight),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false
        );

        assertThat(html).contains("✈️ SFO→<wbr>MUC");
    }

    @Test
    void spacedRouteArrowIsLeftAloneBecauseItAlreadyBreaksAtItsSpaces() {
        CalendarEntry train = new CalendarEntry(
                EntryKind.TRAIN,
                LocalDateTime.of(2026, 6, 10, 9, 0),
                LocalDateTime.of(2026, 6, 10, 13, 0),
                "🚄 Frankfurt → Paris",
                lines("9:00 AM"),
                null, null, null
        );

        String html = CalendarViewBuilder.render(
                List.of(train),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false
        );

        assertThat(html)
                .contains("🚄 Frankfurt → Paris")
                .doesNotContain("<wbr>");
    }

    private static ZonedTimestamp zoned(LocalDateTime local, String zone) {
        return ZonedTimestamp.fromLocal(local, ZoneId.of(zone));
    }

    private static List<SubtitleLine> lines(String... values) {
        return Arrays.stream(values).<SubtitleLine>map(SubtitleLine.Text::new).toList();
    }
}
