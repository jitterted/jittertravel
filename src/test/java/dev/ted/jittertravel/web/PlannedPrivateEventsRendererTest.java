package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedPrivateEventView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedPrivateEventsRendererTest {

    private static final LocalDate AUG_20_2026 = LocalDate.of(2026, 8, 20);
    private static final LocalTime SEVEN_PM = LocalTime.of(19, 0);
    private static final LocalTime TEN_PM = LocalTime.of(22, 0);

    @Test
    void pageIsTitledPlannedPrivateEvents() {
        String html = PlannedPrivateEventsRenderer.render(List.of(), TimeView.FUTURE);

        assertThat(html)
                .contains("<title>Planned Private Events</title>")
                .contains("<h1>Planned Private Events</h1>");
    }

    @Test
    void emptyAllListRendersPlannedYetMessage() {
        String html = PlannedPrivateEventsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html).contains("No private events planned yet.");
    }

    @Test
    void emptyFutureListRendersNoUpcomingMessage() {
        String html = PlannedPrivateEventsRenderer.render(List.of(), TimeView.FUTURE);

        assertThat(html).contains("No upcoming private events.");
    }

    @Test
    void activeFilterMarkedOnToggleLink() {
        String html = PlannedPrivateEventsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("<a href=\"/planned-private-events?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/planned-private-events?filter=future\">Upcoming</a>");
    }

    @Test
    void titleAndVenueNameAreRendered() {
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"private-event-title\">Dinner with the Harrisons</div>")
                .contains("<div class=\"private-event-venue-name\">Barrafina</div>");
    }

    @Test
    void wholeStreetAddressIsRendered() {
        // The reason this page exists: street, region and postal code are carried by no other read
        // model, so this assertion is the only thing pinning that they reach a screen at all.
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"private-event-venue-address\">"
                          + "26 Dean St, London, Greater London W1D 3LL, GB</div>");
    }

    @Test
    void blankAddressPartsAreSkippedRatherThanLeavingStrayPunctuation() {
        PlannedPrivateEventView sparse = new PlannedPrivateEventView(
                PrivateEventId.random(), "Dinner with Susan", "",
                "", "London", "", "", "",
                ukTime(AUG_20_2026, SEVEN_PM), ukTime(AUG_20_2026, TEN_PM));

        String html = PlannedPrivateEventsRenderer.render(List.of(sparse), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"private-event-venue-address\">London</div>")
                .as("no venue name element when the venue is blank")
                .doesNotContain("<div class=\"private-event-venue-name\">");
    }

    @Test
    void dateAndTimesRenderAsTimeElementsCarryingTheUtcInstant() {
        // The element text is the venue-local wall-clock (London BST 19:00), the datetime attribute
        // the same moment in UTC (18:00Z), and data-fmt the pattern a viewer-zone script reuses.
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .contains("<time datetime=\"2026-08-20T18:00:00Z\" data-fmt=\"EEE, MMM d, yyyy\">"
                          + "Thu, Aug 20, 2026</time>")
                .contains("<time datetime=\"2026-08-20T18:00:00Z\" data-fmt=\"h:mm a\">7:00 PM</time>")
                .contains("<time datetime=\"2026-08-20T21:00:00Z\" data-fmt=\"h:mm a\">10:00 PM</time>");
    }

    @Test
    void eachRowLinksToItsCancelPage() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PlannedPrivateEventView privateEvent = new PlannedPrivateEventView(
                privateEventId, "Dinner with the Harrisons", "Barrafina",
                "26 Dean St", "London", "", "W1D 3LL", "GB",
                ukTime(AUG_20_2026, SEVEN_PM), ukTime(AUG_20_2026, TEN_PM));

        String html = PlannedPrivateEventsRenderer.render(List.of(privateEvent), TimeView.FUTURE);

        assertThat(html)
                .contains("<a class=\"private-event-cancel-link\" href=\"/planned-private-events/"
                          + privateEventId.id() + "/cancel\">Cancel</a>");
    }

    @Test
    void cancelIsTheFirstThingInTheActionsCellSoALaterEditLinkCannotShiftIt() {
        // The cell is a column flex: everything added later is appended BELOW this one, so nothing
        // that is here today moves when it ships (CLAUDE.md, affordances never move). "Match
        // location" took that slot on 2026-09-18 and Cancel did not budge.
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"private-event-actions\">"
                          + "<a class=\"private-event-cancel-link\"");
    }

    @Test
    void eachRowLinksToItsMatchingLocationPage() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PlannedPrivateEventView privateEvent = new PlannedPrivateEventView(
                privateEventId, "Dinner with the Harrisons", "Barrafina",
                "26 Dean St", "London", "", "W1D 3LL", "GB",
                ukTime(AUG_20_2026, SEVEN_PM), ukTime(AUG_20_2026, TEN_PM));

        String html = PlannedPrivateEventsRenderer.render(List.of(privateEvent), TimeView.FUTURE);

        assertThat(html)
                .contains("<a class=\"private-event-match-link\" href=\"/planned-private-events/"
                          + privateEventId.id() + "/matching-location\" "
                          + "title=\"Change the city the schedule matches this evening in\">"
                          + "Match location</a>");
    }

    @Test
    void matchLocationFollowsCancelRatherThanPrecedingIt() {
        // Cancel keeps the position it has had since the list shipped; the new link goes under it.
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html.indexOf("private-event-match-link"))
                .as("Match location is appended below Cancel, not above it")
                .isGreaterThan(html.indexOf("private-event-cancel-link"));
    }

    @Test
    void theTwoActionsAreLinksRatherThanAMenu() {
        // Two choices, and a menu starts above three (CLAUDE.md, the dropdown rule) — a menu here
        // would be a door in front of a door.
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("<details")
                .doesNotContain("<summary");
    }

    @Test
    void neitherActionBorrowsAnIcon() {
        // A pencil means edit and nothing else app-wide; re-matching is not the general edit, and
        // a borrowed pencil that opens this page would teach that pencils are unreliable.
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("edit-pencil")
                .doesNotContain("cancel-bin");
    }

    @Test
    void theMatchLocationLinkIsUnderlinedAtRestRatherThanOnHover() {
        // The iPad has no pointer, so a hover-only affordance is invisible at every moment
        // (CLAUDE.md, "never have an affordance that relies on :hover").
        //
        // Both halves are needed, and the first alone is what this test shipped with: asserting
        // the declaration without its selector passes just as happily against
        // `.private-event-match-link:hover { text-decoration: underline; ... }`, which is the exact
        // mutation the test exists to catch. HoverIsNeverTheAffordanceTest does not catch it either
        // — the rule declares its own colour, so that scan's precondition never fires.
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .contains("text-decoration: underline; white-space: nowrap;")
                .as("the underline must not move into a :hover rule")
                .doesNotContain(".private-event-match-link:hover");
    }

    @Test
    void titleIsNotALinkBecauseThereIsNoDetailPageToPointAt() {
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .as("a private event has no infoUrl and no edit page yet")
                .doesNotContain("<a class=\"private-event-title\"")
                .doesNotContain(">Dinner with the Harrisons</a>");
    }

    @Test
    void headerNamesTheFourColumns() {
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"private-event-header\">"
                          + "<span>When</span><span>Private Event</span><span>Venue</span><span></span></div>")
                .as("a private event has no speaking concept, so no such column")
                .doesNotContain("<span>Speaking</span>");
    }

    @Test
    void rowsFillTheWidthAndCollapseOnNarrowViewportsWithoutHorizontalScroll() {
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("max-width: 800px")
                .contains("grid-template-columns: auto 2fr 2fr auto")
                .contains("grid-template-columns: 1fr");
    }

    /**
     * The gathering list's arrangement exactly — icon to the right of the When value, wall-clock
     * and zone rather than the instant. No details parameter: a private event has no infoUrl.
     */
    @Test
    void whenCellCarriesAnAddToGoogleIconWithTheVenuesWallClock() {
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with Sam", "The Ivy")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"private-event-when\">")
                .contains("href=\"https://calendar.google.com/calendar/render?action=TEMPLATE"
                          + "&amp;text=Dinner+with+Sam"
                          + "&amp;dates=20260820T190000/20260820T220000"
                          + "&amp;ctz=Europe%2FLondon"
                          + "&amp;location=The+Ivy%2C+26+Dean+St%2C+London%2C+Greater+London+W1D+3LL%2C+GB\"")
                .contains("class=\"gcal-add\"")
                .contains("viewBox=\"0 0 640 640\"")
                .doesNotContain("&amp;details=");
    }

    private static ZonedTimestamp ukTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), ZoneId.of("Europe/London"));
    }

    private PlannedPrivateEventView view(String title, String venueName) {
        return new PlannedPrivateEventView(
                PrivateEventId.random(), title, venueName,
                "26 Dean St", "London", "Greater London", "W1D 3LL", "GB",
                ukTime(AUG_20_2026, SEVEN_PM), ukTime(AUG_20_2026, TEN_PM));
    }
}
