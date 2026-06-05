package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedGatheringView;
import dev.ted.jittertravel.domain.GatheringId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedGatheringsRendererTest {

    private static final LocalDate AUG_20_2026 = LocalDate.of(2026, 8, 20);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);

    @Test
    void emptyGatheringsRendersEmptyStateMessage() {
        String html = PlannedGatheringsRenderer.render(List.of());

        assertThat(html).contains("No gatherings planned yet.");
    }

    @Test
    void gatheringTitleVenueAndCityCountryAreRendered() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("London Java Community", "Skills Matter", "London", "GB", false, "")
        ));

        assertThat(html)
                .contains("London Java Community")
                .contains("Skills Matter")
                .contains("London, GB");
    }

    @Test
    void dateAndTimeRangeAreFormatted() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "")
        ));

        assertThat(html)
                .contains("Thu, Aug 20, 2026")
                .contains("6:00 PM")
                .contains("9:00 PM");
    }

    @Test
    void speakingTrueRendersSpeakingBadge() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", true, "")
        ));

        assertThat(html).contains("Speaking");
    }

    @Test
    void speakingFalseOmitsSpeakingBadge() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "")
        ));

        assertThat(html).doesNotContain("Speaking");
    }

    @Test
    void presentInfoUrlRendersEventPageLink() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "https://meetup.com/events/123")
        ));

        assertThat(html).contains("https://meetup.com/events/123");
    }

    @Test
    void blankInfoUrlOmitsEventPageLink() {
        String html = PlannedGatheringsRenderer.render(List.of(
                view("Some Meetup", "Venue", "City", "US", false, "")
        ));

        assertThat(html).doesNotContain("Event page");
    }

    private PlannedGatheringView view(String title, String venueName, String city, String country,
                                      boolean speaking, String infoUrl) {
        return new PlannedGatheringView(
                GatheringId.random(), title, venueName, city, country,
                AUG_20_2026, SIX_PM, NINE_PM, speaking, infoUrl);
    }
}
