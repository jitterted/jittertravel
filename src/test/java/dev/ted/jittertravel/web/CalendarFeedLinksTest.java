package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarFeedLinksTest {

    @Test
    void subscribeUrlUsesWebcalSchemeSoItOpensTheIosSubscribeSheet() {
        CalendarFeedLinks links = new CalendarFeedLinks("https://travel.example.com", "TOKEN123");

        assertThat(links.subscribeUrl())
                .isEqualTo("webcal://travel.example.com/calendar/feed/TOKEN123.ics");
    }

    @Test
    void probeAddUrlStaysHttpsForOneOffAddAllImport() {
        CalendarFeedLinks links = new CalendarFeedLinks("https://travel.example.com", "TOKEN123");

        assertThat(links.probeAddUrl())
                .isEqualTo("https://travel.example.com/calendar/feed/TOKEN123/probe.ics");
    }

    @Test
    void probeSubscribeUrlUsesWebcalSchemeToTestTheSubscriptionPath() {
        CalendarFeedLinks links = new CalendarFeedLinks("https://travel.example.com", "TOKEN123");

        assertThat(links.probeSubscribeUrl())
                .isEqualTo("webcal://travel.example.com/calendar/feed/TOKEN123/probe.ics");
    }

    @Test
    void trailingSlashOnTheBaseUrlIsNotDoubledIntoThePath() {
        CalendarFeedLinks links = new CalendarFeedLinks("https://travel.example.com/", "TOKEN123");

        assertThat(links.subscribeUrl())
                .isEqualTo("webcal://travel.example.com/calendar/feed/TOKEN123.ics");
    }

    @Test
    void httpBaseUrlAlsoSwapsToWebcalForSubscription() {
        CalendarFeedLinks links = new CalendarFeedLinks("http://localhost:8080", "TOKEN123");

        assertThat(links.subscribeUrl())
                .isEqualTo("webcal://localhost:8080/calendar/feed/TOKEN123.ics");
        assertThat(links.probeAddUrl())
                .isEqualTo("http://localhost:8080/calendar/feed/TOKEN123/probe.ics");
    }
}
