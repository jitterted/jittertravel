package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With no token configured the feed is DISABLED: every request 404s with an empty body, even one
 * carrying a plausible-looking token. This is the safe, opt-in default — the feed does not exist
 * until {@code CALENDAR_FEED_TOKEN} is set.
 */
@WebMvcTest(CalendarFeedController.class)
@Import(ICalWriter.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "jittertravel.calendar-feed.token=")
class CalendarFeedDisabledTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    CalendarFeedAssembler assembler;

    @Test
    void feedRequestWithAnyTokenReturns404WhenDisabled() {
        assertThat(mockMvc.get().uri("/calendar/feed/anything.ics"))
                .hasStatus(404)
                .bodyText().isEmpty();
    }

    @Test
    void probeRequestWithAnyTokenReturns404WhenDisabled() {
        assertThat(mockMvc.get().uri("/calendar/feed/anything/probe.ics"))
                .hasStatus(404)
                .bodyText().isEmpty();
    }

    @Test
    void anEmptyTokenSegmentDoesNotMatchAnEmptyConfiguredToken() {
        // A blank configured token must never authenticate a blank/empty provided token.
        assertThat(mockMvc.get().uri("/calendar/feed/.ics"))
                .hasStatus(404);
    }
}
