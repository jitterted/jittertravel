package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleContext;
import dev.ted.jittertravel.application.ScheduleProblem;
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
                .contains("<div class=\"pc-band pc-band--bed\" style=\"grid-column: 4 / span 3; grid-row: 2;\">")
                .contains("<div class=\"pc-band-title\">No hotel — London</div>")
                .contains("<div class=\"pc-band-detail\">3 nights</div>");
    }

    @Test
    void bandCrossingAWeekBoundaryRendersOneSegmentPerWeek() {
        // Fri 17 Jul through Tue 21 Jul checkout: four nights, split Fri-Sat then Sun-Mon.
        String html = render(missingHotel("Berlin", 17, 21));

        assertThat(html)
                .contains("<div class=\"pc-band pc-band--bed pc-band--to-right\""
                          + " style=\"grid-column: 6 / span 2; grid-row: 2;\">")
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
                .contains("<div class=\"pc-band pc-band--bed pc-band--to-right\""
                          + " style=\"grid-column: 7 / span 1; grid-row: 2;\">");
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
                .contains("<div class=\"pc-band pc-band--bed\" style=\"grid-column: 4 / span 2; grid-row: 2;\">")
                .contains("<div class=\"pc-band pc-band--travel\" style=\"grid-column: 4 / span 2; grid-row: 3;\">")
                .contains("<div class=\"pc-band-title\">No travel — London → Berlin</div>");
    }

    @Test
    void aTravelGapAloneStillSitsInTheTravelLaneAtTheTopOfTheWeek() {
        // The bed lane is empty, so it consumes no sub-rows and the travel band starts at row 2.
        String html = render(missingTravel("London", 15, "Berlin", 16));

        assertThat(html)
                .contains("<div class=\"pc-band pc-band--travel\" style=\"grid-column: 4 / span 2; grid-row: 2;\">")
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
                .contains("<div class=\"pc-band pc-band--bed\" style=\"grid-column: 4 / span 3; grid-row: 2;\">");
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

    private static String render(List<ScheduleProblem> problems, List<ScheduleContext> context) {
        return ProblemCalendarRenderer.render(problems, context, TODAY);
    }
}
