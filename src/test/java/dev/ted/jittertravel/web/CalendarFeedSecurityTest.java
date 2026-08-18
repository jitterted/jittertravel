package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/**
 * The feed is deliberately unredacted OWNER data guarded only by the URL token, verified against the
 * real secured chain ({@link SecurityConfig}). Two things must both hold:
 * <ul>
 *   <li>the token — not a login session — authenticates, so an <em>anonymous</em> request with the
 *       correct token succeeds and returns the private data (full hotel name present); and</li>
 *   <li>a wrong token leaks nothing: 404, and the private hotel name is absent from the body.</li>
 * </ul>
 */
@WebMvcTest(CalendarFeedController.class)
@Import({SecurityConfig.class, ICalWriter.class})
@TestPropertySource(properties = {
        "TED_PASSWORD=testpass",
        "FAMILY_PASSWORD=testpass",
        "jittertravel.calendar-feed.token=goodtoken"})
class CalendarFeedSecurityTest {

    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

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
    void anonymousRequestWithCorrectTokenSeesTheUnredactedHotelName() {
        given(assembler.feed(NOW)).willReturn(List.of(new ICalEvent(
                "abc-cancelby@jittertravel", NOW, NOW,
                "Free-cancel deadline: Grand Hotel", "Berlin", List.of("-PT24H"))));

        assertThat(mockMvc.get().uri("/calendar/feed/goodtoken.ics").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("Grand Hotel");
    }

    @Test
    void anonymousRequestWithWrongTokenGets404AndNoPrivateData() {
        given(assembler.feed(NOW)).willReturn(List.of(new ICalEvent(
                "abc-cancelby@jittertravel", NOW, NOW,
                "Free-cancel deadline: Grand Hotel", "Berlin", List.of("-PT24H"))));

        assertThat(mockMvc.get().uri("/calendar/feed/wrongtoken.ics").with(anonymous()))
                .hasStatus(404)
                .bodyText()
                .doesNotContain("Grand Hotel");
    }
}
