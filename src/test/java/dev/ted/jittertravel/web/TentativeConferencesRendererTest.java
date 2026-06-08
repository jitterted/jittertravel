package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceView;
import dev.ted.jittertravel.domain.ConferenceId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TentativeConferencesRendererTest {

    @Test
    void emptyListRendersTableWithNoRows() {
        String html = TentativeConferencesRenderer.render(List.of());

        assertThat(html)
                .contains("<table")
                .doesNotContain("<td");
    }

    @Test
    void conferenceNameCityAndCountryAreRendered() {
        String html = TentativeConferencesRenderer.render(List.of(
                view("DDD Europe 2026", "2026-06-07T11:00", "2026-06-10T17:00", "Frankfurt", "Germany")
        ));

        assertThat(html)
                .contains("DDD Europe 2026")
                .contains("Frankfurt")
                .contains("Germany");
    }

    @Test
    void startAndEndDatesAreFormatted() {
        String html = TentativeConferencesRenderer.render(List.of(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ));

        assertThat(html)
                .contains("Sun, Jun 7, 11:00 AM")
                .contains("Wed, Jun 10, 5:00 PM");
    }

    @Test
    void planConferenceLinkIsPresent() {
        String html = TentativeConferencesRenderer.render(List.of());

        assertThat(html).contains("/plan-conference");
    }

    private static TentativeConferenceView view(String name, String start, String end,
                                                String city, String country) {
        return new TentativeConferenceView(
                ConferenceId.random(), name, "Venue",
                new dev.ted.jittertravel.domain.Address("1 Street", city, "", "", country, null),
                LocalDateTime.parse(start), LocalDateTime.parse(end)
        );
    }
}