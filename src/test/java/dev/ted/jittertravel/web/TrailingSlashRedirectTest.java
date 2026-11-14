package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.OneOffTasks;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import dev.ted.jittertravel.infrastructure.TrailingSlashConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * A trailing slash redirects to the path without it, through the real filter and security chain
 * ({@link TrailingSlashConfig}). The anonymous {@code /admin/} case is the one that says the
 * redirect runs <em>before</em> security: after it, the answer would be a login redirect instead.
 */
@Tag("spring")
@WebMvcTest(GeneralController.class)
@Import({SecurityConfig.class, TrailingSlashConfig.class})
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass",
                                  "REMEMBER_ME_KEY=test-remember-me-key"})
class TrailingSlashRedirectTest {

    @Autowired
    MockMvcTester mockMvc;

    // SecurityConfig's remember-me needs a token store, and a @WebMvcTest slice has no DataSource.
    @MockitoBean
    PersistentTokenRepository persistentTokenRepository;

    @MockitoBean
    PostgresPersister persister;

    @MockitoBean
    BuildProperties buildProperties;

    @MockitoBean
    ScheduleGapProjector scheduleGapProjector;

    @MockitoBean
    OneOffTasks oneOffTasks;

    @MockitoBean
    EventStore eventStore;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        lenient().when(buildProperties.getTime()).thenReturn(Instant.EPOCH);
        lenient().when(clock.instant()).thenReturn(Instant.EPOCH);
        lenient().when(persister.countPendingCommands()).thenReturn(0);
        lenient().when(scheduleGapProjector.problems(any())).thenReturn(List.of());
        lenient().when(oneOffTasks.outstanding()).thenReturn(List.of());
    }

    @Test
    void trailingSlashRedirectsToThePathWithoutItBeforeSecurityIsAsked() {
        assertThat(mockMvc.get().uri("/admin/"))
                .as("Anonymous /admin/ redirects to /admin, not to the login page")
                .hasStatus(HttpStatus.PERMANENT_REDIRECT)
                .hasHeader("Location", "/admin");
    }

    @Test
    void theQueryStringRidesAlong() {
        assertThat(mockMvc.get().uri("/calendar/?day=2026-07-01"))
                .as("The redirect keeps the query string")
                .hasStatus(HttpStatus.PERMANENT_REDIRECT)
                .hasHeader("Location", "/calendar?day=2026-07-01");
    }

    @Test
    void theRootPathIsNotRedirected() {
        assertThat(mockMvc.get().uri("/"))
                .as("The home page is served, not redirected to itself")
                .hasStatusOk();
    }
}
