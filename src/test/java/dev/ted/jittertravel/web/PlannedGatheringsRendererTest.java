package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedGatheringView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedGatheringsRendererTest {

    private static final LocalDate AUG_20_2026 = LocalDate.of(2026, 8, 20);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);

    @Test
    void emptyAllListRendersPlannedYetMessage() {
        String html = PlannedGatheringsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html).contains("No gatherings planned yet.");
    }

    @Test
    void emptyFutureListRendersNoUpcomingMessage() {
        String html = PlannedGatheringsRenderer.render(List.of(), TimeView.FUTURE);

        assertThat(html).contains("No upcoming gatherings.");
    }

    @Test
    void activeFilterMarkedOnToggleLink() {
        String html = PlannedGatheringsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("<a href=\"/planned-gatherings?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/planned-gatherings?filter=future\">Upcoming</a>");
    }

    @Test
    void gatheringTitleVenueAndCityCountryAreRendered() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("London Java Community", "Skills Matter", "London", "GB", false, "")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("London Java Community")
                .contains("Skills Matter")
                .contains("London, GB");
    }

    @Test
    void dateAndTimeRangeAreFormatted() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("Thu, Aug 20, 2026")
                .contains("6:00 PM")
                .contains("9:00 PM");
    }

    @Test
    void speakingTrueRendersSpeakingBadge() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", true, "")
        ), TimeView.FUTURE);

        assertThat(html).contains("Speaking");
    }

    @Test
    void dateAndTimesRenderAsTimeElementsCarryingTheUtcInstant() {
        // Baseline of the browser-zone display: the element text is the venue-local wall-clock
        // (London BST 18:00), the datetime attribute the same moment in UTC (17:00Z), and
        // data-fmt the pattern a viewer-zone script would reuse.
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("<time datetime=\"2026-08-20T17:00:00Z\" data-fmt=\"EEE, MMM d, yyyy\">"
                          + "Thu, Aug 20, 2026</time>")
                .contains("<time datetime=\"2026-08-20T17:00:00Z\" data-fmt=\"h:mm a\">6:00 PM</time>")
                .contains("<time datetime=\"2026-08-20T20:00:00Z\" data-fmt=\"h:mm a\">9:00 PM</time>");
    }

    @Test
    void speakingFalseOmitsSpeakingBadge() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "")
        ), TimeView.FUTURE);

        assertThat(html).doesNotContain("Speaking");
    }

    @Test
    void presentInfoUrlRendersEventPageLink() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "https://meetup.com/events/123")
        ), TimeView.FUTURE);

        assertThat(html).contains("https://meetup.com/events/123");
    }

    @Test
    void blankInfoUrlOmitsEventPageLink() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "")
        ), TimeView.FUTURE);

        assertThat(html).doesNotContain("Event page");
    }

    @Test
    void eachCardLinksToItsEditPage() {
        GatheringId gatheringId = GatheringId.random();
        PlannedGatheringView gathering = new PlannedGatheringView(
                gatheringId, "Some Meetup", "Venue", "", "City", "", "", "US",
                ukTime(AUG_20_2026, SIX_PM), ukTime(AUG_20_2026, NINE_PM), false, "");

        String html = PlannedGatheringsRenderer.render(List.of(gathering), TimeView.FUTURE);

        assertThat(html)
                .contains("<a class=\"gathering-edit-link\" href=\"/planned-gatherings/"
                        + gatheringId.id() + "\">Edit</a>");
    }

    private static ZonedTimestamp ukTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), ZoneId.of("Europe/London"));
    }

    private PlannedGatheringView view(String title, String venueName, String city, String country,
                                      boolean speaking, String infoUrl) {
        return new PlannedGatheringView(
                GatheringId.random(), title, venueName, "", city, "", "", country,
                ukTime(AUG_20_2026, SIX_PM), ukTime(AUG_20_2026, NINE_PM), speaking, infoUrl);
    }
}
