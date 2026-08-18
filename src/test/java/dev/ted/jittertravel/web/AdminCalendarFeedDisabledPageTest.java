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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With no token configured the admin card shows the disabled state and no feed URLs — there is no
 * token to leak into the page.
 */
@WebMvcTest(AdminController.class)
@WithMockUser(roles = "OWNER")
@TestPropertySource(properties = "jittertravel.calendar-feed.token=")
class AdminCalendarFeedDisabledPageTest {

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
    void showsDisabledStateAndNoFeedUrlsWhenNoTokenConfigured() {
        assertThat(mockMvc.get().uri("/admin/calendar-feed"))
                .hasStatusOk()
                .bodyText()
                .contains("disabled")
                .contains("CALENDAR_FEED_TOKEN")
                // No feed URL of any kind is rendered — there is no token to place in one.
                .doesNotContain("/calendar/feed/")
                .doesNotContain("webcal://");
    }
}
