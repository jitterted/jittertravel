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
        // The cell is a column flex: the edit flow's future "Edit" is appended BELOW this one, so
        // nothing that is here today moves when it ships (CLAUDE.md, affordances never move).
        String html = PlannedPrivateEventsRenderer.render(List.of(
                view("Dinner with the Harrisons", "Barrafina")), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"private-event-actions\">"
                          + "<a class=\"private-event-cancel-link\"");
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
