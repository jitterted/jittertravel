package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceView;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TentativeConferencesRendererTest {

    // The test JVM is pinned to UTC (pom.xml), so an explicit venue zone is what proves the
    // rendered text is the venue's wall-clock rather than the server's.
    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    @Test
    void emptyAllListRendersEmptyStateMessage() {
        String html = TentativeConferencesRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("No tentative conferences yet.")
                .doesNotContain("<td");
    }

    @Test
    void emptyFutureListRendersNoUpcomingMessage() {
        String html = TentativeConferencesRenderer.render(List.of(), TimeView.FUTURE);

        assertThat(html).contains("No upcoming conferences.");
    }

    @Test
    void activeFilterMarkedOnToggleLink() {
        String html = TentativeConferencesRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("<a href=\"/tentative-conferences?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/tentative-conferences?filter=future\">Upcoming</a>");
    }

    @Test
    void conferenceNameCityAndCountryAreRendered() {
        String html = TentativeConferencesRenderer.render(List.of(
                view("DDD Europe 2026", "2026-06-07T11:00", "2026-06-10T17:00", "Frankfurt", "Germany")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("DDD Europe 2026")
                .contains("Frankfurt")
                .contains("Germany");
    }

    @Test
    void startAndEndDatesAreFormatted() {
        String html = TentativeConferencesRenderer.render(List.of(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ), TimeView.FUTURE);

        // Date and time are separate .nowrap spans so a narrow cell breaks only between them
        // (never mid-value); there is no longer a comma joining date and time.
        assertThat(html)
                .contains("<span class=\"nowrap\">Sun, Jun 7</span>")
                .contains("<span class=\"nowrap\">11:00 AM</span>")
                .contains("<span class=\"nowrap\">Wed, Jun 10</span>")
                .contains("<span class=\"nowrap\">5:00 PM</span>");
    }

    @Test
    void tableIsNotWrappedInAHorizontalScroller() {
        String html = TentativeConferencesRenderer.render(List.of(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ), TimeView.FUTURE);

        // No page may ever scroll sideways: the fix removed both the overflow-x scroller and the
        // container width cap in favour of content that stacks.
        assertThat(html)
                .doesNotContain("overflow-x")
                .doesNotContain("table-responsive")
                .doesNotContain("max-width: 100ch");
    }

    @Test
    void planConferenceLinkIsPresent() {
        String html = TentativeConferencesRenderer.render(List.of(), TimeView.ALL);

        assertThat(html).contains("/plan-conference");
    }

    private static TentativeConferenceView view(String name, String start, String end,
                                                String city, String country) {
        return new TentativeConferenceView(
                ConferenceId.random(), name, "Venue",
                new Address("1 Street", city, "", "", country, null),
                ZonedTimestamp.fromLocal(LocalDateTime.parse(start), ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.parse(end), ZONE)
        );
    }
}