package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.ConferenceMigrationService;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OWNER-only calendar-feed card renders the subscribe + probe links (each carrying the token)
 * and, with no configured base URL, derives the host from the request. The token reaching a
 * non-owner is covered by {@link AuthorizationMatrixTest}.
 */
@WebMvcTest(AdminController.class)
@WithMockUser(roles = "OWNER")
@TestPropertySource(properties = "jittertravel.calendar-feed.token=feedsecret")
class AdminCalendarFeedPageTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-05-31T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        BackupSource backupSource() {
            return new BackupSource("");
        }
    }

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    BackupService backupService;
    @MockitoBean
    PostgresPersister persister;
    @MockitoBean
    TentativeConferenceProjector tentativeConferenceProjector;
    @MockitoBean
    ConferenceMigrationService conferenceMigrationService;
    @MockitoBean
    LegacyEventMigration legacyEventMigration;

    @Test
    void rendersSubscribeAndBothProbeLinksCarryingTheToken() {
        assertThat(mockMvc.get().uri("/admin/calendar-feed"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
                .bodyText()
                // base URL unset -> derived from the request (MockMvc uses http://localhost:80).
                .contains("webcal://localhost/calendar/feed/feedsecret.ics")
                .contains("http://localhost/calendar/feed/feedsecret/probe.ics")
                .contains("webcal://localhost/calendar/feed/feedsecret/probe.ics");
    }

    @Test
    void showsTheValidationGateInstructionToTurnRemoveAlertsOff() {
        assertThat(mockMvc.get().uri("/admin/calendar-feed"))
                .hasStatusOk()
                .bodyText()
                // The money-critical instruction: iOS defaults "Remove Alerts" ON (which silently
                // suppresses the deadline alarms), so the card must tell the owner to turn it off.
                .contains("Remove Alerts");
    }
}
