package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.OneOffTaskView;
import dev.ted.jittertravel.application.OneOffTasks;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import java.time.Clock;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Verifies login-related behavior and the rendered home-page navigation per role under the
 * secured chain. Pure route authorization (role × route → outcome) lives in
 * {@link AuthorizationMatrixTest}.
 */
@WebMvcTest(GeneralController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass",
                                  "REMEMBER_ME_KEY=test-remember-me-key"})
class SecurityAuthorizationTest {

    @Autowired
    MockMvcTester mockMvc;

    // SecurityConfig's remember-me needs a token store, and a @WebMvcTest slice has no DataSource
    // for the JDBC one. Mocking the interface keeps the filter chain whole: the cookie is still
    // minted and cancelled through the real PersistentTokenBasedRememberMeServices.
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
        lenient().when(scheduleGapProjector.problems(any())).thenReturn(List.of());
        lenient().when(oneOffTasks.outstanding()).thenReturn(List.of());
    }

    @Test
    @WithAnonymousUser
    void homePageIsPublicAndRendersHomeNotTheLoginForm() {
        given(persister.countPendingCommands()).willReturn(0);

        // Regression: failureUrl("/") made DefaultLoginPageGeneratingFilter render the login
        // form at "/" (HTTP 200), so a status-only check passed while the home page was hidden.
        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("wordmark-text")
                .doesNotContain("Please sign in")
                .doesNotContain("name=\"password\"");
    }

    @Test
    @WithAnonymousUser
    void anonymousHomeShowsCalendarOnly() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("/calendar")
                .doesNotContain("/itinerary")
                .doesNotContain("/booked-flights")
                .doesNotContain("/book-flight")
                .doesNotContain(">Admin</span>");
    }

    @Test
    @WithAnonymousUser
    void anonymousHomeNeverShowsThePostDeployTaskBanner() {
        // The banner names migrations and pending data work, so it is OWNER-only — unlike the
        // read-only banner, which is deliberately shown to everyone. Tasks are outstanding here:
        // the claim is that the viewer, not the state, is what keeps the banner off the page.
        given(persister.countPendingCommands()).willReturn(0);
        given(oneOffTasks.outstanding()).willReturn(List.of(outstandingTask()));

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("need doing after the latest deploy")
                .doesNotContain("needs doing after the latest deploy")
                .doesNotContain("href=\"/admin/tasks\"");
    }

    @Test
    @WithMockUser(roles = "FAMILY")
    void familyHomeNeverShowsThePostDeployTaskBanner() {
        given(persister.countPendingCommands()).willReturn(0);
        given(oneOffTasks.outstanding()).willReturn(List.of(outstandingTask()));

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("need doing after the latest deploy")
                .doesNotContain("needs doing after the latest deploy")
                .doesNotContain("href=\"/admin/tasks\"");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerHomeShowsThePostDeployTaskBanner() {
        // The other half of the claim: the banner really would have rendered for an owner, so the
        // two tests above are about the viewer and not about an empty list.
        given(persister.countPendingCommands()).willReturn(0);
        given(oneOffTasks.outstanding()).willReturn(List.of(outstandingTask()));

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("1 task needs doing after the latest deploy")
                .contains("href=\"/admin/tasks\"");
    }

    @Test
    @WithAnonymousUser
    void anonymousHomeNeverShowsThePendingCommandsBanner() {
        // Same reasoning as the task banner: it names a count of admin internals and links into
        // /admin/pending-commands. Commands are pending here, so the claim is about the viewer.
        given(persister.countPendingCommands()).willReturn(3);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("still pending")
                .doesNotContain("href=\"/admin/pending-commands\"");
    }

    @Test
    @WithMockUser(roles = "FAMILY")
    void familyHomeNeverShowsThePendingCommandsBanner() {
        given(persister.countPendingCommands()).willReturn(3);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("still pending")
                .doesNotContain("href=\"/admin/pending-commands\"");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerHomeStillShowsThePendingCommandsBanner() {
        given(persister.countPendingCommands()).willReturn(3);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("3 commands are still pending")
                .contains("href=\"/admin/pending-commands\"");
    }

    private static OneOffTaskView outstandingTask() {
        return new OneOffTaskView("normalize-event-log-type", "Run the migration", "Detail",
                "/admin/migrate-legacy-events", "Open it", LocalDate.of(2026, 8, 19), null);
    }

    @Test
    @WithMockUser(roles = "FAMILY")
    void familyHomeShowsItineraryAndCalendarOnly() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("/itinerary")
                .contains("/calendar")
                .doesNotContain("/booked-flights")
                .doesNotContain("/book-flight")
                .doesNotContain(">Admin</span>");
    }

    @Test
    @WithAnonymousUser
    void failedLoginRedirectsToLoginPageWithError() {
        assertThat(mockMvc.post().uri("/login")
                .param("username", "ted")
                .param("password", "wrong-password")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/login?error");
    }

    @Test
    @WithAnonymousUser
    void staleCsrfTokenReturnsToLoginWithExpiredNotice() {
        // A login POST with no valid CSRF token (the session that minted it is gone — a
        // server restart or an idle timeout) must land back on the login page with a notice,
        // NOT silently on the home page, where an expired login is indistinguishable from
        // never having signed in. Omitting .with(csrf()) reproduces the missing/rejected token.
        assertThat(mockMvc.post().uri("/login")
                .param("username", "ted")
                .param("password", "testpass"))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/login?expired");
    }

    @Test
    @WithAnonymousUser
    void loginWithRememberMeTickedSetsAPersistentCookie() {
        // The whole point of the feature: the cookie is what survives a restart, since the
        // HttpSession does not. A max-age well past a session cookie's is what makes it
        // persistent, so assert on that rather than merely on the cookie existing.
        MvcTestResult result = mockMvc.post().uri("/login")
                .param("username", "ted")
                .param("password", "testpass")
                .param("remember-me", "on")
                .with(csrf())
                .exchange();

        Cookie rememberMe = result.getResponse().getCookie("remember-me");
        assertThat(rememberMe)
                .as("a remember-me cookie is set when the box is ticked")
                .isNotNull();
        assertThat(rememberMe.getMaxAge())
                .as("cookie outlives the session, for the configured 30 days")
                .isEqualTo((int) Duration.ofDays(30).toSeconds());
        assertThat(rememberMe.isHttpOnly())
                .as("a credential is never readable by page scripts")
                .isTrue();
    }

    @Test
    @WithAnonymousUser
    void loginWithoutRememberMeSetsNoPersistentCookie() {
        // Unticking the box is the only "not on this device" control the app has — there is no
        // logout affordance — so it has to actually suppress the cookie.
        MvcTestResult result = mockMvc.post().uri("/login")
                .param("username", "ted")
                .param("password", "testpass")
                .with(csrf())
                .exchange();

        assertThat(result.getResponse().getCookie("remember-me"))
                .as("no remember-me cookie when the box is unticked")
                .isNull();
    }

    @Test
    @WithMockUser(username = "ted", roles = "OWNER")
    void successfulLogoutReturnsToLoginPageWithSignedOutNotice() {
        // login.html renders a "signed out" notice on ?logout, and until 2026-09-08
        // logoutSuccessUrl sent a successful logout to "/" instead — so the notice was
        // unreachable in production and a deliberate sign-out looked exactly like arriving
        // signed out. LoginControllerTest pins that the notice renders; this pins that a
        // logout actually goes to the URL that shows it.
        assertThat(mockMvc.post().uri("/logout")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/login?logout");
    }

    @Test
    @WithAnonymousUser
    void successfulLoginCapturesReportedBrowserZoneAsCookie() {
        MvcTestResult result = mockMvc.post().uri("/login")
                .param("username", "ted")
                .param("password", "testpass")
                .param("browserZone", "Europe/Berlin")
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        Cookie zoneCookie = result.getResponse().getCookie("viewerZone");
        assertThat(zoneCookie)
                .as("a valid browserZone from the login form is stored so the first "
                    + "authenticated render already knows the viewer's zone")
                .isNotNull();
        assertThat(zoneCookie.getValue()).isEqualTo("Europe/Berlin");
    }

    @Test
    @WithAnonymousUser
    void successfulLoginWithUnrecognizedBrowserZoneSetsNoCookie() {
        MvcTestResult result = mockMvc.post().uri("/login")
                .param("username", "ted")
                .param("password", "testpass")
                .param("browserZone", "Not/AZone")
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getCookie("viewerZone"))
                .as("a bad zone is dropped rather than stored; the reader then falls back")
                .isNull();
    }
}
