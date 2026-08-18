package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Behavior of {@link CalendarFeedController} with the security filters off — token logic, status,
 * content type. The security chain itself (that anonymous requests reach this controller and that a
 * wrong token leaks nothing through the real chain) is covered by {@link CalendarFeedSecurityTest}.
 */
@WebMvcTest(CalendarFeedController.class)
@Import(ICalWriter.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "jittertravel.calendar-feed.token=goodtoken")
class CalendarFeedControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar");

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    CalendarFeedAssembler assembler;

    @Test
    void correctTokenReturns200TextCalendarWithTheVevent() {
        given(assembler.feed(NOW)).willReturn(List.of(new ICalEvent(
                "abc-cancelby@jittertravel", NOW, NOW,
                "Free-cancel deadline: Grand Hotel", "Berlin", List.of("-PT24H"))));

        assertThat(mockMvc.get().uri("/calendar/feed/goodtoken.ics"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(TEXT_CALENDAR)
                .bodyText()
                .contains("BEGIN:VCALENDAR")
                .contains("BEGIN:VEVENT")
                .contains("Free-cancel deadline: Grand Hotel");
    }

    @Test
    void wrongTokenReturns404WithEmptyBody() {
        assertThat(mockMvc.get().uri("/calendar/feed/wrongtoken.ics"))
                .hasStatus(404)
                .bodyText().isEmpty();
    }

    @Test
    void probeWithCorrectTokenReturns200WithASingleSyntheticVevent() {
        given(assembler.probeEvent(NOW)).willReturn(new ICalEvent(
                "probe@jittertravel", NOW, NOW,
                "JitterTravel test reminder — safe to delete", "", List.of("-PT5M")));

        assertThat(mockMvc.get().uri("/calendar/feed/goodtoken/probe.ics"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(TEXT_CALENDAR)
                .bodyText()
                .contains("JitterTravel test reminder")
                .contains("TRIGGER;RELATED=START:-PT5M");
    }

    @Test
    void probeWithWrongTokenReturns404() {
        assertThat(mockMvc.get().uri("/calendar/feed/wrongtoken/probe.ics"))
                .hasStatus(404)
                .bodyText().isEmpty();
    }
}
