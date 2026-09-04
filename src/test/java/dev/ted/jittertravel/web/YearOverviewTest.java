package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
import dev.ted.jittertravel.application.EntryKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The year overview's own direct test. It is a self-contained static renderer, so it gets one —
 * reaching it only through the page that embeds it is what let a renamed day-menu item and an added
 * one both ship green (CLAUDE.md).
 * <p>
 * The panel says two things and no more (Ted, 2026-09-01): a <strong>tint</strong> for the reason
 * Ted is somewhere — conference, gathering, or the hotel under both — and a <strong>plane</strong>
 * for the days he flew. Everything else is deliberately silent.
 */
class YearOverviewTest {

    private static final LocalDate GRID_START = LocalDate.of(2026, 8, 30);   // a Sunday
    private static final LocalDate GRID_END = LocalDate.of(2026, 10, 31);    // a Saturday
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    private static String render(List<CalendarEntry> entries) {
        return render(entries, Set.of(), false);
    }

    private static String render(List<CalendarEntry> entries, Set<LocalDate> awayDays, boolean isPublicUser) {
        return YearOverview.render(entries, GRID_START, GRID_END, TODAY, awayDays, isPublicUser).render();
    }

    private static CalendarEntry entry(EntryKind kind, LocalDate start, LocalDate end) {
        return new CalendarEntry(start.atTime(9, 0), end.atTime(17, 0),
                kind.name(), List.of(), detailsFor(kind));
    }

    private static CalendarEntry onDay(EntryKind kind, LocalDate day) {
        return entry(kind, day, day);
    }

    /** Exhaustive, so a new kind cannot be added without this test learning how to build one. */
    private static EntryDetails detailsFor(EntryKind kind) {
        return switch (kind) {
            case CONFERENCE -> new EntryDetails.Conference(null, false, null);
            case GATHERING -> new EntryDetails.Gathering(null, false, null);
            case PRIVATE_EVENT -> new EntryDetails.PrivateEvent();
            case FLIGHT -> new EntryDetails.Flight(null);
            case TRAIN -> new EntryDetails.Train(null);
            case GROUND_TRANSFER -> new EntryDetails.GroundTransfer(null);
            case LODGING -> new EntryDetails.Lodging(null, null);
        };
    }

    // --- layout ---------------------------------------------------------------------------------

    @Test
    void rendersOneMiniPerMonthTheGridTouchesIncludingAPartialFirstMonth() {
        String html = render(List.of());

        // Aug 30-31 are in the grid, so August gets a mini even though the range is "September on".
        assertThat(html)
                .contains("data-month=\"2026-08\"")
                .contains("data-month=\"2026-09\"")
                .contains("data-month=\"2026-10\"")
                .doesNotContain("data-month=\"2026-07\"")
                .doesNotContain("data-month=\"2026-11\"");
    }

    @Test
    void eachMonthLinksToTheAnchorIdTheCalendarEmitsForIt() {
        assertThat(render(List.of()))
                .contains("<a href=\"#m-2026-09\" class=\"yo-month\" data-month=\"2026-09\">")
                .contains("<a href=\"#m-2026-10\" class=\"yo-month\" data-month=\"2026-10\">")
                .contains("<span class=\"yo-month-label\">Sep 2026</span>");
    }

    @Test
    void theWholeMiniIsTheClickTargetNotJustItsLabel() {
        // Ted, 2026-09-01. A 15px day cell is far under a comfortable touch target and days are not
        // individually clickable, so nothing inside competes for the tap.
        String september = between(render(List.of()), "data-month=\"2026-09\"", "data-month=\"2026-10\"");

        assertThat(september.indexOf("<span class=\"yo-grid\""))
                .as("the grid opens before the anchor closes, so the days are inside the link")
                .isLessThan(september.indexOf("</a>"));
    }

    @Test
    void eachMiniCarriesAWeekdayHeaderRow() {
        // Seven anonymous columns become "that mark is on a Tuesday" — the point of aligning them.
        assertThat(render(List.of()))
                .contains("<span class=\"yo-dow\">S</span><span class=\"yo-dow\">M</span>"
                          + "<span class=\"yo-dow\">T</span><span class=\"yo-dow\">W</span>"
                          + "<span class=\"yo-dow\">T</span><span class=\"yo-dow\">F</span>"
                          + "<span class=\"yo-dow\">S</span>");
    }

    @Test
    void thePanelHasNoTitleRowButKeepsItsCloseButton() {
        // The title named the range ("Aug 2026 – Oct 2026"), which the first and last minis already
        // say, and it cost a line of the panel (Ted, 2026-09-01). The close button is a control
        // rather than information, so it stays.
        assertThat(render(List.of()))
                .doesNotContain("yo-panel-title")
                .doesNotContain("Aug 2026 – Oct 2026")
                .contains("class=\"yo-close\"");
    }

    @Test
    void thereIsNoLegend() {
        // It went with the seven-colour palette it existed to decode. Three tints and one glyph
        // need no key — and the tints are the ones the week grid below already uses.
        assertThat(render(List.of(onDay(EntryKind.CONFERENCE, LocalDate.of(2026, 9, 14)))))
                .doesNotContain("yo-legend")
                .doesNotContain("yo-swatch")
                .doesNotContain("away from home");
    }

    // --- weekday alignment ----------------------------------------------------------------------

    /**
     * The mini's whole promise: seven anonymous columns only become "that mark is on a Tuesday" if
     * the 1st is indented to its own weekday. A Monday-first rewrite, or an off-by-one in the
     * modulo, shifts every mark one column while every other case here stays green.
     */
    @Test
    void theFirstOfTheMonthIsIndentedToItsOwnWeekdayColumn() {
        // Sep 1 2026 is a Tuesday — and is TODAY, so its cell is identifiable inside the run.
        assertThat(between(render(List.of()), "data-month=\"2026-09\"", "data-month=\"2026-10\""))
                .contains("<span class=\"yo-dow\">S</span>"
                          + "<span class=\"yo-day yo-blank\"></span>"
                          + "<span class=\"yo-day yo-blank\"></span>"
                          + "<span class=\"yo-day yo-today\"></span>");
    }

    @Test
    void aMonthStartingOnASundayGetsNoLeadingBlanksAndOpensOnTheWeekendColumn() {
        // Nov 1 2026 is a Sunday, so the first cell after the header IS the 1st — which is also
        // what pins the grid as Sunday-first rather than Monday-first.
        String november = YearOverview.render(
                List.of(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 28),
                TODAY, Set.of(), false).render();

        assertThat(november)
                .contains("<span class=\"yo-dow\">S</span><span class=\"yo-day yo-weekend\"></span>")
                .doesNotContain("yo-blank");
    }

    // --- the colour cascade: conference > gathering > hotel, and nothing else tints -------------

    @Test
    void aConferenceOutranksTheHotelUnderneathIt() {
        LocalDate day = LocalDate.of(2026, 9, 15);
        String html = render(List.of(
                entry(EntryKind.LODGING, day.minusDays(1), day.plusDays(1)),
                onDay(EntryKind.CONFERENCE, day)));

        assertThat(html)
                .contains("<span class=\"yo-day yo-filled yo-conference\"></span>")
                .as("the nights either side are still the hotel's")
                .contains("<span class=\"yo-day yo-filled yo-lodging\"></span>");
    }

    @Test
    void aConferenceOutranksAGatheringOnTheSameDay() {
        LocalDate day = LocalDate.of(2026, 9, 15);
        String html = render(List.of(
                onDay(EntryKind.GATHERING, day),
                onDay(EntryKind.CONFERENCE, day)));

        assertThat(html)
                .contains("yo-conference")
                .doesNotContain("yo-gathering");
    }

    @Test
    void aGatheringOutranksTheHotelUnderneathIt() {
        LocalDate day = LocalDate.of(2026, 9, 15);
        String html = render(List.of(
                entry(EntryKind.LODGING, day.minusDays(1), day.plusDays(1)),
                onDay(EntryKind.GATHERING, day)));

        assertThat(html).contains("<span class=\"yo-day yo-filled yo-gathering\"></span>");
    }

    @Test
    void aHotelNightWithNothingElseOnItStillTints() {
        assertThat(render(List.of(entry(EntryKind.LODGING, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12)))))
                .contains("<span class=\"yo-day yo-filled yo-lodging\"></span>");
    }

    @Test
    void travelTintsNothingAtAll() {
        // A busy month used to lose a day of its trip to every travel leg, so the trip stopped
        // reading as one block. A flight speaks through its plane; a train and a taxi say nothing.
        String html = render(List.of(
                onDay(EntryKind.FLIGHT, LocalDate.of(2026, 9, 14)),
                onDay(EntryKind.TRAIN, LocalDate.of(2026, 9, 16)),
                onDay(EntryKind.GROUND_TRANSFER, LocalDate.of(2026, 9, 18))));

        assertThat(html)
                .doesNotContain("yo-flight")
                .doesNotContain("yo-train")
                .doesNotContain("yo-ground_transfer")
                .as("no kind tinted the day, so nothing is filled")
                .doesNotContain("yo-filled");
    }

    @Test
    void aPrivateEventTintsNothing() {
        // The map answers "where am I going and when", and a Tuesday dinner at home is not that.
        assertThat(render(List.of(onDay(EntryKind.PRIVATE_EVENT, LocalDate.of(2026, 9, 19)))))
                .doesNotContain("yo-private_event")
                .doesNotContain("yo-filled");
    }

    @Test
    void theColourPriorityHoldsExactlyTheKindsThatColour() {
        // Two declarations that must not drift: the ORDER lives in COLOUR_PRIORITY, the yes/no in
        // the exhaustive coloursTheDay switch, which is what stops a new EntryKind compiling until
        // someone decides. A kind in one and not the other renders nothing, or throws.
        assertThat(YearOverview.COLOUR_PRIORITY)
                .containsExactly(EntryKind.CONFERENCE, EntryKind.GATHERING, EntryKind.LODGING)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(EntryKind.values()).filter(YearOverview::coloursTheDay).toList());
    }

    // --- the plane, and its absence -------------------------------------------------------------

    @Test
    void everyFlightDayWearsThePlane() {
        // Every one, not just a run's first: a flight is a single day, and two on one day (a layover
        // entered as two flights) is still one day Ted moved.
        String html = render(List.of(
                onDay(EntryKind.FLIGHT, LocalDate.of(2026, 9, 14)),
                onDay(EntryKind.FLIGHT, LocalDate.of(2026, 9, 18))));

        assertThat(html.split("✈", -1).length - 1)
                .as("one plane per flight day")
                .isEqualTo(2);
    }

    @Test
    void thePlaneSitsOnTheDaysOwnColourRatherThanReplacingIt() {
        // Two independent channels: the tint says what the trip is, the plane says Ted moved. So a
        // conference flown home from on its last day still shows that day as a conference day.
        LocalDate lastDay = LocalDate.of(2026, 9, 17);
        String html = render(List.of(
                entry(EntryKind.CONFERENCE, lastDay.minusDays(3), lastDay),
                onDay(EntryKind.FLIGHT, lastDay)));

        assertThat(html)
                .contains("<span class=\"yo-day yo-filled yo-conference yo-flying\">✈</span>");
    }

    @Test
    void aTripWhoseFlightsAreNotBookedYetWearsNoPlanes() {
        // The absence is the signal Ted asked for: a future trip with no planes on either end is one
        // whose flights he has not booked.
        String html = render(List.of(
                entry(EntryKind.LODGING, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)),
                entry(EntryKind.CONFERENCE, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 17))));

        assertThat(html)
                .contains("yo-conference")
                .contains("yo-lodging")
                .doesNotContain("✈")
                .doesNotContain("yo-flying");
    }

    @Test
    void thePlaneIsTheTextGlyphNotTheColourEmoji() {
        // U+2708 with NO U+FE0F. The variation selector forces emoji presentation — the
        // blue-and-white picture — which ignores `color` and fights the pale tint underneath it.
        // An earlier version added the selector and pinned it with a test; that was backwards.
        assertThat(YearOverview.PLANE)
                .isEqualTo("✈")
                .hasSize(1);
        assertThat(render(List.of(onDay(EntryKind.FLIGHT, LocalDate.of(2026, 9, 14)))))
                .doesNotContain(Character.toString(0xFE0F));
    }

    // --- away band, today, and the days the grid did not draw ------------------------------------

    @Test
    void anAwayDayIsMarkedEvenWhenItCarriesNoEntryAtAll() {
        // ScheduleTimeline.walk() fills the nights BETWEEN points, so a trip flown out and back with
        // no hotel booked yet bands days holding no entries — exactly the days the band warns about.
        // Any code reasoning "no entry => nothing to draw" silently drops them.
        assertThat(render(List.of(), Set.of(LocalDate.of(2026, 9, 17)), false))
                .contains("<span class=\"yo-day yo-away\"></span>");
    }

    @Test
    void todayIsOutlinedAndCanAlsoBeAnAwayDay() {
        assertThat(render(List.of(), Set.of(TODAY), false))
                .as("both edge treatments on one cell — they must compose, not overwrite")
                .contains("<span class=\"yo-day yo-away yo-today\"></span>");
    }

    @Test
    void daysOfAMonthTheCalendarDidNotDrawAreMutedNotColoured() {
        // Aug 1-29 are in August's mini but not on the page. They keep weekday alignment honest
        // while reading as "not shown here" rather than "nothing happening".
        assertThat(between(render(List.of()), "data-month=\"2026-08\"", "data-month=\"2026-09\""))
                .contains("yo-off");
    }

    // --- audience -------------------------------------------------------------------------------

    @Test
    void anonymousGetsNoOverlayMarkupAtAll() {
        assertThat(render(List.of(onDay(EntryKind.CONFERENCE, LocalDate.of(2026, 9, 14))), Set.of(), true))
                .isEmpty();
    }

    @Test
    void familyGetsTheFullOverlay() {
        // The gate is isPublicUser, never isOwner. The trap is next door: dayMenu is owner-only, so
        // an implementer working from that line gates this on isOwner and FAMILY silently loses the
        // overlay with every other test still green. YearOverview never sees isOwner at all.
        assertThat(render(List.of(onDay(EntryKind.FLIGHT, LocalDate.of(2026, 9, 14)))))
                .contains("class=\"disclosure-menu year-overview\"")
                .contains("class=\"year-overview-trigger\"")
                .contains("yo-flying");
    }

    private static String between(String html, String start, String end) {
        return html.substring(html.indexOf(start), html.indexOf(end));
    }
}
