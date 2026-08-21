package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleContext;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemCalendarRendererTest {

    // A Wednesday mid-month: a band starting on it lands in column 4 of Sunday..Saturday, and no
    // day in its week is a month start, which would relabel that cell "Jul 1" rather than a number.
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    @Test
    void noProblemsRendersCleanMessageAndNoGrid() {
        String html = render();

        assertThat(html)
                .contains("<title>Schedule Problems</title>")
                .contains("No problems found")
                .doesNotContain("class=\"pc-container\"");
    }

    @Test
    void pageCarriesTheSelectorWithCalendarActive() {
        String html = render();

        assertThat(html)
                .contains("<div class=\"view-toggle\">")
                .contains("<a href=\"/schedule-problems?view=list\">List</a>")
                .contains("<a href=\"/schedule-problems?view=calendar\" class=\"active\">Calendar</a>");
    }

    @Test
    void missingHotelRendersAsABedBandAcrossItsNights() {
        // Wed 15 Jul through Sat 18 Jul checkout: three nights, columns 4..6 of the week.
        String html = render(missingHotel("London", 15, 18));

        assertThat(html)
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 4 / span 3; grid-row: 2;\">")
                .contains("<div class=\"pc-band pc-band--bed\">")
                .contains("<div class=\"pc-band-title\">No hotel — London</div>")
                .contains("<div class=\"pc-band-detail\">3 nights</div>");
    }

    @Test
    void bandCrossingAWeekBoundaryRendersOneSegmentPerWeek() {
        // Fri 17 Jul through Tue 21 Jul checkout: four nights, split Fri-Sat then Sun-Mon.
        String html = render(missingHotel("Berlin", 17, 21));

        assertThat(html)
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 6 / span 2; grid-row: 2;\">")
                .contains("<div class=\"pc-band pc-band--bed pc-band--to-right\">")
                .contains("<div class=\"pc-band pc-band--bed pc-band--from-left\""
                          + " style=\"grid-column: 1 / span 2; grid-row: 2;\">");
    }

    @Test
    void overlappingBandsInTheSameLaneStackIntoSeparateSubRows() {
        String html = render(missingHotel("London", 15, 17), missingHotel("Berlin", 16, 18));

        assertThat(html)
                .contains("style=\"grid-column: 4 / span 2; grid-row: 2;\"")
                .contains("style=\"grid-column: 5 / span 2; grid-row: 3;\"");
    }

    @Test
    void nonOverlappingBandsInTheSameLaneShareOneSubRow() {
        String html = render(missingHotel("London", 15, 16), missingHotel("Berlin", 17, 18));

        assertThat(html)
                .contains("style=\"grid-column: 4 / span 1; grid-row: 2;\"")
                .contains("style=\"grid-column: 6 / span 1; grid-row: 2;\"")
                .doesNotContain("grid-row: 3;");
    }

    @Test
    void gridSpansSundayToSaturdayWeeksWithSevenEqualColumns() {
        String html = render(missingHotel("London", 15, 16));

        assertThat(html)
                .contains("<div class=\"pc-header\"><div>Sunday</div>")
                .contains("<div>Saturday</div></div>")
                .contains("grid-template-columns: repeat(7, minmax(0, 1fr));")
                .doesNotContain("repeat(7, 1fr)");
    }

    @Test
    void todayIsMarkedAndEarlierDaysInItsWeekAreMarkedPast() {
        String html = render(missingHotel("London", 15, 16));

        assertThat(html)
                .contains("<div class=\"pc-day-cell is-today\">")
                .contains("<div class=\"pc-day-cell is-past\">");
    }

    @Test
    void windowReachesBackToABandThatStartedBeforeToday() {
        // A stay already under way can still be missing a bed for tonight, so the grid must show
        // its first night even though that night is behind us.
        String html = render(missingHotel("London", 4, 17));

        assertThat(html)
                .contains(">Jun 28, 2026<")   // the Sunday of the week holding Saturday 4 July
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 7 / span 1; grid-row: 2;\">")
                .contains("<div class=\"pc-band pc-band--bed pc-band--to-right\">");
    }

    @Test
    void windowAlwaysShowsAtLeastTheFortnightAhead() {
        String html = render(missingHotel("London", 15, 16));

        // today + 2 weeks is 29 July, whose week runs to Saturday 1 August (a month start, so it
        // is labelled "Aug 1"). Nothing past it renders: the 2nd would show as a bare ">2<".
        assertThat(html)
                .contains(">Aug 1<")
                .doesNotContain(">2<");
    }

    @Test
    void travelGapsRenderInTheirOwnLaneUnderTheBedLane() {
        String html = render(missingHotel("London", 15, 17), missingTravel("London", 15, "Berlin", 16));

        assertThat(html)
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 4 / span 2; grid-row: 2;\">")
                .contains("<div class=\"pc-band pc-band--bed\">")
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 4 / span 2; grid-row: 3;\">")
                .contains("<div class=\"pc-band pc-band--travel\">")
                .contains("<div class=\"pc-band-title\">No travel — London → Berlin</div>");
    }

    @Test
    void aTravelGapAloneStillSitsInTheTravelLaneAtTheTopOfTheWeek() {
        // The bed lane is empty, so it consumes no sub-rows and the travel band starts at row 2.
        String html = render(missingTravel("London", 15, "Berlin", 16));

        assertThat(html)
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 4 / span 2; grid-row: 2;\">")
                .contains("<div class=\"pc-band pc-band--travel\">")
                // the class name also appears in the page's own CSS, so name the whole element
                .doesNotContain("<div class=\"pc-band pc-band--bed\"");
    }

    @Test
    void contextRendersAsAGreyBackdropBehindTheProblemBands() {
        // The conference runs Wed..Fri (columns 4..6) and the lane block is two rows tall: one for
        // the bed band, one holding the context label.
        String html = render(
                List.of(missingHotel("Chicago", 15, 18)),
                List.of(conference("dev2next", "Chicago", 15, 17)));

        assertThat(html)
                .contains("<div class=\"pc-context\" style=\"grid-column: 4 / span 3; grid-row: 2 / span 2;\">")
                .contains("<span class=\"pc-context-label\">dev2next, Chicago · Jul 15–17</span>")
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 4 / span 3; grid-row: 2;\">")
                .contains("<div class=\"pc-band pc-band--bed\">");
    }

    @Test
    void contextIsDrawnBeforeTheProblemBandsSoTheBandsPaintOverIt() {
        String html = render(
                List.of(missingHotel("Chicago", 15, 18)),
                List.of(conference("dev2next", "Chicago", 15, 17)));

        assertThat(html.indexOf("class=\"pc-context\""))
                .as("the backdrop must precede the problem band in document order — later siblings "
                    + "paint on top, and the band has to sit over its context, not under it")
                .isLessThan(html.indexOf("class=\"pc-band pc-band--bed\""));
    }

    @Test
    void overlappingContextLabelsStackInsteadOfColliding() {
        // Two context bands on the same days: each reserves its own line at the foot of the week,
        // the second lifted clear of the first, and the lane block grows to hold both.
        String html = render(
                List.of(missingHotel("Chicago", 15, 18)),
                List.of(conference("dev2next", "Chicago", 15, 17),
                        new ScheduleContext.Stay("Chicago",
                                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 18))));

        assertThat(html)
                .contains("grid-row: 2 / span 3;")
                .contains("padding-bottom: 1.15em;")
                .contains("<span class=\"pc-context-label\">Hotel, Chicago · Jul 15–18</span>")
                .contains("<span class=\"pc-context-label\">dev2next, Chicago · Jul 15–17</span>");
    }

    @Test
    void contextOutsideTheProblemWindowNeverReachesThePage() {
        // The window is drawn from the problems; a December conference is not a cause of a July gap.
        String html = render(
                List.of(missingHotel("London", 15, 16)),
                List.of(new ScheduleContext.Conference("J-Fall", "Ede",
                        LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 4))));

        assertThat(html).doesNotContain("J-Fall");
    }

    @Test
    void contextCrossingAWeekBoundaryIsSquaredOffOnThatSide() {
        String html = render(
                List.of(missingHotel("Berlin", 17, 21)),
                List.of(conference("SoCraTes", "Soltau", 17, 20)));

        assertThat(html)
                .contains("<div class=\"pc-context pc-context--to-right\"")
                .contains("<div class=\"pc-context pc-context--from-left\"");
    }

    // --- Clash lane (slice 4) ---

    @Test
    void bothClashKindsRenderInTheOneClashLaneUnderTheTravelLane() {
        // Three days of one week: a gap on Tue 14 (column 3), a city clash on Thu 16 (column 5), a
        // scheduling clash on Fri 17 (column 6). The clash lane sits below travel, so its bands are
        // one row lower — and the two clash markers share that lane, so two bands that do not
        // overlap in days share its single sub-row instead of reserving a row per kind.
        String html = render(missingTravel("London", 14, "Berlin", 14),
                cityConflict(16), schedulingConflict(17));

        assertThat(html)
                .contains("<div class=\"pc-band pc-band--travel\">")
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 3 / span 1; grid-row: 2;\">")
                .contains("<div class=\"pc-band-anchor\" style=\"grid-column: 5 / span 1; grid-row: 3;\">")
                .contains("<div class=\"pc-band pc-band--clash-city\">")
                .contains("<div class=\"pc-band-title\">City clash — Lunch · dev2next</div>")
                .contains("<div class=\"pc-band-detail\">Denver vs Chicago</div>")
                .contains("<div class=\"pc-band pc-band--clash-scheduling\""
                          + " style=\"grid-column: 6 / span 1; grid-row: 3;\">")
                .contains("<div class=\"pc-band-title\">Clash — XP Day · Lunch</div>");
    }

    /**
     * Ted missed a run of missing hotels on this calendar because they were blue (2026-08-21). A
     * band's first job is to say something is wrong, so every kind now shares one amber fill and
     * keeps its kind only as the left edge. The absence assertions are the real claim: a per-kind
     * <em>fill</em> must not come back.
     */
    @Test
    void everyBandWearsTheSameAmberAndCarriesItsKindOnlyOnTheLeftEdge() {
        String html = render(missingHotel("London", 15, 18));

        assertThat(html)
                .contains("--pc-problem-bg: rgba(254, 243, 199, 0.85);")
                .contains("background: var(--pc-problem-bg); color: var(--pc-problem-fg);")
                .contains("border-left-color: var(--pc-bed-border);")
                .doesNotContain("--pc-bed-bg")
                .doesNotContain("--pc-travel-bg")
                .doesNotContain("--pc-duplicate-bg")
                .doesNotContain("--pc-clash-city-bg")
                .doesNotContain("--pc-clash-scheduling-bg");
    }

    /**
     * C3: a city clash is the one clash with something to do about it, and it is the same
     * {@code /clear-conflict} URL the list card offers.
     */
    @Test
    void aCityClashBandLinksStraightToClearingTheConflict() {
        String html = render(cityConflict(15));

        assertThat(html)
                .contains("class=\"pc-band-link\">")
                .contains("<span class=\"pc-band-fix\">Clear this conflict &rarr;</span>")
                .doesNotContain("<summary class=\"pc-band-summary\">");
    }

    /**
     * F6: neither side of a scheduling clash carries an id, so there is nothing to link to — and
     * unlike a card there is no slot vocabulary to keep, so the band simply is not clickable.
     */
    @Test
    void aSchedulingClashBandIsNotAnAnchorAtAll() {
        String html = render(schedulingConflict(15));

        assertThat(html)
                .contains("<div class=\"pc-band pc-band--clash-scheduling\"")
                .doesNotContain("<details class=\"disclosure-menu\">")
                .doesNotContain("<summary class=\"pc-band-summary\">");
    }

    private static ScheduleProblem cityConflict(int dayOfJuly) {
        return new ScheduleProblem.DifferentCityConflict(
                "Lunch", "Denver", "dev2next", "Chicago", LocalDate.of(2026, 7, dayOfJuly),
                GatheringId.random(), ConferenceId.random());
    }

    private static ScheduleProblem schedulingConflict(int dayOfJuly) {
        return new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("XP Day", "London",
                        at(dayOfJuly, 9, 0), at(dayOfJuly, 17, 0)),
                new ScheduleProblem.ConflictingGathering("Lunch", "London",
                        at(dayOfJuly, 12, 0), at(dayOfJuly, 13, 0)));
    }

    private static ScheduleProblem missingHotel(String city, int checkInDayOfJuly, int checkOutDayOfJuly) {
        return new ScheduleProblem.MissingHotel(city,
                LocalDate.of(2026, 7, checkInDayOfJuly), LocalDate.of(2026, 7, checkOutDayOfJuly), "");
    }

    private static ScheduleProblem missingTravel(String fromCity, int arrivalDayOfJuly,
                                                 String toCity, int departureDayOfJuly) {
        return new ScheduleProblem.MissingTravel(
                fromCity, at(arrivalDayOfJuly, 14, 30),
                toCity, at(departureDayOfJuly, 9, 0));
    }

    private static ZonedTimestamp at(int dayOfJuly, int hour, int minute) {
        return ZonedTimestamp.fromLocal(
                LocalDateTime.of(2026, 7, dayOfJuly, hour, minute), ZoneId.of("Europe/London"));
    }

    private static ScheduleContext conference(String name, String city, int firstDayOfJuly, int lastDayOfJuly) {
        return new ScheduleContext.Conference(name, city,
                LocalDate.of(2026, 7, firstDayOfJuly), LocalDate.of(2026, 7, lastDayOfJuly));
    }

    private static String render(ScheduleProblem... problems) {
        return render(List.of(problems), List.of());
    }

    // --- Fix menus on bands (slice 5) ---

    /**
     * The band <em>is</em> the summary: the whole band is already the click target, so it gains no
     * chrome and no height and the week rows keep their shape.
     */
    @Test
    void aBandWithOneAnswerIsALinkToItAndSaysSoOnItsFace() {
        String html = render(missingHotel("London", 15, 18));

        assertThat(html)
                .contains("<a href=\"/book-hotel?city=London&amp;checkIn=2026-07-15&amp;checkOut=2026-07-18\" "
                          + "class=\"pc-band-link\">")
                .contains("<span class=\"pc-band-fix\">Book hotel &rarr;</span>")
                .as("one answer needs no menu, and a band that only looks clickable is a hidden affordance")
                .doesNotContain("<details class=\"disclosure-menu\">");
    }

    /**
     * Clicking the middle of a run reads as clicking that day, and two menus for one problem would
     * be two things to dismiss — so only the first segment is an anchor.
     */
    @Test
    void aContinuationSegmentIsInertWithNoMenuOfItsOwn() {
        // Fri 17 Jul through Tue 21 Jul: the Sun-Mon half is a continuation.
        String html = render(missingHotel("Berlin", 17, 21));

        assertThat(html)
                .containsOnlyOnce("class=\"pc-band-link\">")
                .containsOnlyOnce("<span class=\"pc-band-fix\">Book hotel &rarr;</span>")
                .as("the continuation stays a plain band, carrying its own grid placement")
                .contains("<div class=\"pc-band pc-band--bed pc-band--from-left\""
                          + " style=\"grid-column: 1 / span 2; grid-row: 2;\">");
    }

    /**
     * C3, stated as a test: the band and the list card link to the same place, because both read
     * {@code ProblemFix.forProblem}. Two URL builders is how the two views drift apart.
     */
    @Test
    void aBandAndItsListCardOfferTheSameHrefs() {
        ScheduleProblem problem = missingTravel("London", 15, "Berlin", 16);

        String calendar = render(problem);
        String list = ScheduleProblemsRenderer.render(List.of(problem));

        // A travel gap has three answers, so the band keeps a menu while the card lists its links:
        // different controls, deliberately, but the same destinations.
        for (ProblemFix fix : ProblemFix.forProblem(problem)) {
            assertThat(calendar).contains("<a href=\"" + fix.href().replace("&", "&amp;") + "\" "
                                          + "class=\"disclosure-menu-item\">" + fix.label() + "</a>");
            assertThat(list).contains("<a href=\"" + fix.href().replace("&", "&amp;") + "\" "
                                      + "class=\"fix-summary\">" + fix.label() + " &rarr;</a>");
        }
    }

    @Test
    void theMenuDismissalScriptShipsWithTheCalendarPage() {
        String html = render(missingHotel("London", 15, 18));

        assertThat(html)
                .as("without it the menus open and never close")
                .contains("closeDisclosureMenus");
    }

    private static String render(List<ScheduleProblem> problems, List<ScheduleContext> context) {
        return ProblemCalendarRenderer.render(problems, context, TODAY);
    }
}
