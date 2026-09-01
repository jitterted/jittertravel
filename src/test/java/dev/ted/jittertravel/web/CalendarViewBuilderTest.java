package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarViewBuilderTest {

    // "today" pinned far before every range below, so existing assertions see
    // neither is-past nor is-today markup. Past/today behavior has dedicated tests.
    private static final LocalDate TODAY = LocalDate.of(2020, 1, 1);

    // Details for a kind carrying nothing the test cares about: no links, no chips. A test that
    // is about a link or a chip builds its own details inline, so the interesting value is
    // visible at the point it matters.
    private static final EntryDetails CONFERENCE_DETAILS = new EntryDetails.Conference(null, false, null);
    private static final EntryDetails FLIGHT_DETAILS = new EntryDetails.Flight(null);
    private static final EntryDetails TRAIN_DETAILS = new EntryDetails.Train(null);

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

    /**
     * The sticky month bands are gone (2026-09-01), and this is the arrangement that killed them.
     * <p>
     * A week was filed under the month its <strong>Sunday</strong> fell in, so with a grid starting
     * Sun Aug 30 the week holding <strong>Sep 1–5 rendered under a band reading "AUGUST 2026"</strong>
     * — not merely late, actively wrong. They had been added for "i completely lose what month it
     * is", an orientation problem while scrolling to find a month; the year overview answers that by
     * jumping, so they lost the job they were built for (Ted, 2026-09-01).
     * <p>
     * What still names a month is the 1st's own day label, and the jump anchor the overview targets.
     */
    @Test
    void noMonthBandRidesAboveTheWeeks() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 8, 25),
                false
        );

        assertThat(html)
                .doesNotContain("calendar-month-header")
                .as("the 1st still names its own month, in its day label")
                .contains("Sep 1")
                .as("and still carries the year overview's jump anchor")
                .contains("id=\"m-2026-09\"");
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
                .containsOnlyOnce("<details class=\"disclosure-menu\"")
                .contains("href=\"/itinerary?date=2026-06-20\"")
                .contains("href=\"/book-flight?date=2026-06-20\"")
                .contains("href=\"/book-train?date=2026-06-20\"")
                .contains("href=\"/book-hotel?date=2026-06-20\"")
                .contains("href=\"/plan-ground-transfer?date=2026-06-20\"")
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
                .doesNotContain("disclosure-menu")
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
                .doesNotContain("disclosure-menu")
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
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 4, 17, 0),
                "DevConf",
                lines("(Portland, USA)"),
                "DevConf cont'd",
                lines("(Portland, USA)"),
                CONFERENCE_DETAILS
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
                LocalDateTime.of(2026, 6, 6, 13, 55),
                LocalDateTime.of(2026, 6, 7, 9, 45),
                "✈️ SFO\u2192FRA",
                lines("Departs 1:55 PM"),
                null,
                lines("Arr 9:45 AM"),
                FLIGHT_DETAILS
        );
        // Conference DDD Europe 2026: Sun 2026-06-07 11:00 -> Wed 2026-06-10 17:00.
        CalendarEntry conf = new CalendarEntry(
                LocalDateTime.of(2026, 6, 7, 11, 0),
                LocalDateTime.of(2026, 6, 10, 17, 0),
                "DDD Europe 2026",
                lines("(Frankfurt, Germany)"),
                "DDD Europe 2026 cont'd",
                lines("(Frankfurt, Germany)"),
                CONFERENCE_DETAILS
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
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 4, 17, 0),
                "ConfA", lines("(City, Country)"),
                "ConfA cont'd", lines("(City, Country)"),
                CONFERENCE_DETAILS
        );
        CalendarEntry b = new CalendarEntry(
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 5, 17, 0),
                "ConfB", lines("(City, Country)"),
                "ConfB cont'd", lines("(City, Country)"),
                CONFERENCE_DETAILS
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
                LocalDateTime.of(2026, 6, 10, 15, 0),
                LocalDateTime.of(2026, 6, 12, 11, 0),
                "Grand Hotel Berlin",
                lines("Berlin, DE"),
                "Grand Hotel Berlin cont'd",
                lines("Berlin, DE"),
                new EntryDetails.Lodging("https://maps.google.com/?q=Grand+Hotel+Berlin", null)
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
                LocalDateTime.of(2026, 6, 8, 9, 0),
                LocalDateTime.of(2026, 6, 8, 17, 0),
                "Conf", lines("(City, Country)"),
                "Conf cont'd", lines("(City, Country)"),
                CONFERENCE_DETAILS
        );
        CalendarEntry flight = new CalendarEntry(
                LocalDateTime.of(2026, 6, 9, 9, 0),
                LocalDateTime.of(2026, 6, 9, 13, 0),
                "✈️ A→B", lines("Departs 9:00 AM"),
                null, lines("Arr 1:00 PM"),
                FLIGHT_DETAILS
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
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 3, 17, 0),
                "PastConf", lines("(City, Country)"),
                "PastConf cont'd", lines("(City, Country)"),
                CONFERENCE_DETAILS
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
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 16, 17, 0),
                "FutureConf", lines("(City, Country)"),
                "FutureConf cont'd", lines("(City, Country)"),
                CONFERENCE_DETAILS
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
                LocalDateTime.of(2026, 6, 10, 18, 0),
                LocalDateTime.of(2026, 6, 10, 21, 0),
                "London Java Community",
                lines("Skills Matter", "London, GB"),
                new EntryDetails.Gathering("https://meetup.com/ljc/events/123", false, null)
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
                LocalDateTime.of(2026, 6, 10, 18, 0),
                LocalDateTime.of(2026, 6, 10, 21, 0),
                "London Java Community",
                lines("Skills Matter", "London, GB"),
                new EntryDetails.Gathering("https://meetup.com/ljc/events/123", true, null)
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
    void speculativeConferenceRendersMaybeChip() {
        String html = CalendarViewBuilder.render(
                List.of(conference(AttendanceCommitment.WATCHING)),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8),
                TODAY,
                false
        );

        assertThat(html).contains("<span class=\"entry-maybe-badge\">Maybe</span>");
    }

    @Test
    void committedConferenceRendersNoChipAtAll() {
        // "Ted is going" is the default reading of a calendar entry, so a Going chip would be
        // noise on every committed conference — and its absence something a reader has to reason
        // about.
        String html = CalendarViewBuilder.render(
                List.of(conference(AttendanceCommitment.GOING)),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8),
                TODAY,
                false
        );

        assertThat(html)
                .contains(">J-Fall<")
                .doesNotContain("entry-maybe-badge")
                .doesNotContain(">Maybe<");
    }

    @Test
    void continuationSegmentOfASpeculativeConferenceCarriesNoChip() {
        // Like the title and the pencil, the chip belongs to the entry's own segment: repeating it
        // on every week the conference spans would read as a second, separate maybe.
        CalendarEntry multiWeek = new CalendarEntry(
                LocalDateTime.of(2026, 11, 5, 9, 0),
                LocalDateTime.of(2026, 11, 12, 18, 0),
                "J-Fall", lines("Ede, Netherlands"),
                "J-Fall cont'd", lines("Ede, Netherlands"),
                new EntryDetails.Conference(AttendanceCommitment.WATCHING, false, null)
        );

        String secondWeek = CalendarViewBuilder.render(
                List.of(multiWeek),
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 15),
                TODAY,
                false
        );

        assertThat(secondWeek)
                .contains("<span>J-Fall cont&#x27;d</span>")
                .doesNotContain("entry-maybe-badge");
    }

    @Test
    void aCommittedConferenceTedSpeaksAtWearsTheSpeakingBadge() {
        String html = CalendarViewBuilder.render(
                List.of(conference(AttendanceCommitment.GOING, true)),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8),
                TODAY,
                false
        );

        assertThat(html)
                .contains("<span class=\"entry-speaking-badge\">A Ted Talk</span>")
                .doesNotContain("<span class=\"entry-maybe-badge\">Maybe</span>");
    }

    /**
     * The renderer draws what it is handed and never re-derives anything — but the pairing it would
     * take to reach this is not constructible upstream: the projectors set the speaking flag only
     * on a committed conference, so "Maybe" plus a speaking badge cannot arise.
     */
    @Test
    void aSpeculativeConferenceWearsTheMaybeChipAndNeverBothChips() {
        String html = CalendarViewBuilder.render(
                List.of(conference(AttendanceCommitment.WATCHING, true)),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8),
                TODAY,
                false
        );

        assertThat(html)
                .contains("<span class=\"entry-maybe-badge\">Maybe</span>")
                .doesNotContain("<span class=\"entry-speaking-badge\">A Ted Talk</span>");
    }

    /**
     * A conference's own page hangs off its title, exactly as a gathering's does — both are public
     * events, and CLAUDE.md publishes a conference in full, {@code infoUrl} included. Owner and
     * anonymous entries reach this same code, which is why the details type carries it rather than
     * the renderer deciding.
     */
    @Test
    void conferenceTitleLinksToItsOwnPage() {
        CalendarEntry conference = new CalendarEntry(
                LocalDateTime.of(2026, 11, 5, 9, 0),
                LocalDateTime.of(2026, 11, 5, 18, 0),
                "J-Fall", lines("Ede, Netherlands"),
                new EntryDetails.Conference(AttendanceCommitment.WATCHING, false, "https://jfall.nl/")
        );

        String html = CalendarViewBuilder.render(
                List.of(conference),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8),
                TODAY,
                true
        );

        assertThat(html).contains("href=\"https://jfall.nl/\"");
    }

    @Test
    void conferenceWithNoPageOfItsOwnHasNoTitleLink() {
        String html = CalendarViewBuilder.render(
                List.of(conference(AttendanceCommitment.WATCHING)),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8),
                TODAY,
                true
        );

        assertThat(html)
                .contains("J-Fall")
                .as("no page recorded means no link, not a link to nowhere")
                .doesNotContain("<a href=\"http");
    }

    private static CalendarEntry conference(AttendanceCommitment commitment) {
        return conference(commitment, false);
    }

    private static CalendarEntry conference(AttendanceCommitment commitment, boolean speaking) {
        return new CalendarEntry(
                LocalDateTime.of(2026, 11, 5, 9, 0),
                LocalDateTime.of(2026, 11, 5, 18, 0),
                "J-Fall", lines("Ede, Netherlands"),
                new EntryDetails.Conference(commitment, speaking, null)
        );
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

    /**
     * A ground transfer has no edit page, so the same slot carries a cancel instead — which is why
     * {@code cancelPath} is a field of its own rather than an overloaded {@code editPath}: the icon
     * and the verb differ, and no kind sets both, so nothing moves between rows.
     */
    @Test
    void ownerEntryWithCancelPathShowsCancelBinInThePencilsSlot() {
        String html = CalendarViewBuilder.render(
                List.of(transferWithCancelPath()),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false,   // isPublicUser
                true     // isOwner
        );

        assertThat(html)
                .contains("class=\"cancel-bin\" href=\"/ground-transfers/gt-123/cancel\"")
                .doesNotContain("class=\"edit-pencil\"");
    }

    @Test
    void nonOwnerEntryWithCancelPathHasNoCancelBin() {
        String html = CalendarViewBuilder.render(
                List.of(transferWithCancelPath()),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false,   // isPublicUser
                false    // isOwner
        );

        assertThat(html)
                .doesNotContain("cancel-bin")
                .doesNotContain("/ground-transfers/");
    }

    private static CalendarEntry transferWithCancelPath() {
        return new CalendarEntry(
                LocalDateTime.of(2026, 6, 10, 12, 0),
                LocalDateTime.of(2026, 6, 10, 12, 45),
                "🚕 DEN → Marriott Lone Tree",
                lines("12:00 PM"),
                new EntryDetails.GroundTransfer("/ground-transfers/gt-123/cancel")
        );
    }

    private static CalendarEntry gatheringWithEditPath() {
        return new CalendarEntry(
                LocalDateTime.of(2026, 6, 10, 18, 0),
                LocalDateTime.of(2026, 6, 10, 21, 0),
                "London Java Community",
                lines("Skills Matter", "London, GB"),
                new EntryDetails.Gathering("https://meetup.com/ljc/events/123", false,
                                           "/planned-gatherings/g-123")
        );
    }

    @Test
    void subtitleTimeRangeRendersAsTimeElementsCarryingTheUtcInstant() {
        // A same-day SFO→LAX hop: 9:00 AM PDT is 16:00Z, 10:30 AM PDT is 17:30Z. The segment
        // still sits in its entry-zone day column; only the rendered time carries the instant.
        ZonedTimestamp departure = zoned(LocalDateTime.of(2026, 6, 10, 9, 0), "America/Los_Angeles");
        ZonedTimestamp arrival = zoned(LocalDateTime.of(2026, 6, 10, 10, 30), "America/Los_Angeles");
        CalendarEntry flight = new CalendarEntry(
                departure.localDateTime(),
                arrival.localDateTime(),
                "SFO to LAX",
                List.of(new SubtitleLine.Range(departure, arrival)),
                FLIGHT_DETAILS
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
                departure.localDateTime(),
                departure.localDateTime(),
                "London to Paris",
                List.of(new SubtitleLine.At("Departs", departure)),
                TRAIN_DETAILS
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
                LocalDateTime.of(2026, 6, 10, 9, 0),
                LocalDateTime.of(2026, 6, 10, 13, 0),
                "✈️ SFO→MUC",
                lines("9:00 AM"),
                FLIGHT_DETAILS
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
                LocalDateTime.of(2026, 6, 10, 9, 0),
                LocalDateTime.of(2026, 6, 10, 13, 0),
                "🚄 Frankfurt → Paris",
                lines("9:00 AM"),
                TRAIN_DETAILS
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

    @Test
    void awayDaysCarryTheBandClassAndTheDaysAroundThemDoNot() {
        // The band's whole rendering contract: the marked cells are exactly the days handed in.
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 13),
                TODAY,
                false,
                false,
                Set.of(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 10))
        );

        assertThat(html)
                .contains("<div class=\"day-label-cell month-tint-even is-away\"><a href=\"/itinerary?date=2026-06-09\"")
                .contains("<div class=\"day-label-cell month-tint-even is-away\"><a href=\"/itinerary?date=2026-06-10\"")
                .contains("<div class=\"day-label-cell month-tint-even\"><a href=\"/itinerary?date=2026-06-08\"")
                .contains("<div class=\"day-label-cell month-tint-even\"><a href=\"/itinerary?date=2026-06-11\"");
    }

    @Test
    void anAwayDayInACollapsedPastWeekKeepsItsBand() {
        // The reason the band lives on the day-label row at all: that row is the one thing a
        // collapsed week still renders, so past trips keep showing their rhythm.
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 15),
                false,
                false,
                Set.of(LocalDate.of(2026, 6, 9))
        );

        assertThat(html)
                .contains("calendar-week--collapsed")
                .contains("<div class=\"day-label-cell month-tint-even is-past is-away\"><a href=\"/itinerary?date=2026-06-09\"");
    }

    /**
     * The private-event lane sits between the conference's indigo and the gathering's violet, and
     * a fill alone could not tell three neighbouring purples apart at week-grid scale. The glyph
     * is what separates them, so it is worth pinning that it fronts the title rather than merely
     * appearing somewhere in the entry.
     */
    @Test
    void privateEventTitleIsFrontedByTheUtensilsGlyph() {
        CalendarEntry dinner = new CalendarEntry(
                LocalDateTime.of(2026, 6, 10, 19, 0),
                LocalDateTime.of(2026, 6, 10, 22, 0),
                "Dinner with the Smiths",
                lines("Toronto, CA"),
                new EntryDetails.PrivateEvent()
        );

        String html = CalendarViewBuilder.render(
                List.of(dinner),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                false
        );

        assertThat(html)
                .contains("entry entry--private_event")
                .as("the glyph opens the title div, ahead of the title's own span")
                .contains("<div class=\"entry-title\"><span class=\"entry-kind-icon\">")
                .as("it is the same fork-and-knife the Private event nav card wears")
                .contains("viewBox=\"0 0 448 512\"")
                .as("and it tints with the lane rather than carrying the nav card's own fill")
                .contains("fill=\"currentColor\"")
                .doesNotContain("fill=\"#6b6860\"");
    }

    /**
     * {@link EntryDetails.Busy} is the public face of the very same kind. The glyph would tell an
     * anonymous viewer the block is a meal — the kind of evening, which CLAUDE.md keeps off the
     * public calendar. Keyed on the details type, so this holds without the builder ever asking
     * who is looking. The security-chain half of this claim is in {@code CalendarRedactionSecurityTest}.
     */
    @Test
    void theBusyBarThatHidesAPrivateEventCarriesNoGlyph() {
        CalendarEntry busy = new CalendarEntry(
                LocalDateTime.of(2026, 6, 10, 19, 0),
                LocalDateTime.of(2026, 6, 10, 22, 0),
                "Busy",
                lines("Toronto, CA"),
                new EntryDetails.Busy()
        );

        String html = CalendarViewBuilder.render(
                List.of(busy),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 14),
                TODAY,
                true
        );

        assertThat(html)
                .as("it is the same lane, so the fill is shared")
                .contains("entry entry--private_event")
                .as("but the title stands alone")
                .contains("<div class=\"entry-title\"><span>Busy</span>")
                .doesNotContain("<span class=\"entry-kind-icon\">")
                .as("nor the glyph's path data by any other route")
                .doesNotContain("M33.1 0C42");
    }

    private static ZonedTimestamp zoned(LocalDateTime local, String zone) {
        return ZonedTimestamp.fromLocal(local, ZoneId.of(zone));
    }

    private static List<SubtitleLine> lines(String... values) {
        return Arrays.stream(values).<SubtitleLine>map(SubtitleLine.Text::new).toList();
    }
}
