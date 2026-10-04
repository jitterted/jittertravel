package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * The probe through the <em>real</em> security chain, with no {@code csrf()} shortcut: the token the
 * page renders and the cookie it sets are the only credentials the POST has, exactly as in a
 * browser. A CSRF failure sends the viewer to {@code /login?expired}, which looks like being logged
 * out, so this is the test that says the button works end to end.
 */
@Tag("spring")
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass",
                                  "REMEMBER_ME_KEY=test-remember-me-key"})
@WithMockUser(roles = "OWNER")
class FamilyNotifyProbeCsrfTest {

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
    PersistentTokenRepository persistentTokenRepository;
    @MockitoBean
    BackupService backupService;
    @MockitoBean
    PostgresPersister persister;
    @MockitoBean
    LegacyEventMigration legacyEventMigration;
    @MockitoBean
    BrevoEmailClient brevo;
    @MockitoBean
    FamilyTestMemory memory;

    /**
     * A login clears the CSRF cookie and Spring only writes it again when something reads the token,
     * normally the first form a page renders. A big page can start sending before that, and its
     * POST is then rejected as an expired session. So the cookie has to exist after any request,
     * including for a page with no form at all (the calendar-feed card has none), which is only true
     * if {@code CsrfCookieFilter} is in the real chain. This class has a context of its own and
     * never uses {@code csrf()}, which would otherwise swap the repository out from under it.
     */
    @Test
    void everyRequestWritesTheCsrfCookieEvenForAPageThatNeverReadsTheToken() throws Exception {
        MvcTestResult page = mockMvc.get().uri("/admin/calendar-feed").exchange();

        assertThat(page.getResponse().getContentAsString())
                .as("this page renders no form, so nothing on it reads the token")
                .doesNotContain("<form");
        assertThat(page.getResponse().getCookie("XSRF-TOKEN"))
                .as("the cookie is written up front, not left to whichever page renders a form first")
                .isNotNull();
    }

    @Test
    void aRequestThatAlreadyHasTheCookieIsNotGivenANewOneSoAnOpenFormStaysValid() throws Exception {
        MvcTestResult page = mockMvc.get().uri("/admin/calendar-feed")
                .cookie(new Cookie("XSRF-TOKEN", "existing")).exchange();

        assertThat(page.getResponse().getCookie("XSRF-TOKEN"))
                .isNull();
    }

    @Test
    void theButtonOnThePageWorksWithOnlyTheTokenAndCookieThePageGave() throws Exception {
        given(brevo.configured()).willReturn(true);
        given(brevo.recipient()).willReturn("ted@example.com");

        MvcTestResult page = mockMvc.get().uri("/admin").exchange();
        String html = page.getResponse().getContentAsString();
        Matcher token = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(token.find())
                .as("the page renders a CSRF token in the probe form")
                .isTrue();
        Cookie cookie = page.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie)
                .as("and the response sets the XSRF-TOKEN cookie it is checked against")
                .isNotNull();

        assertThat(mockMvc.post().uri("/admin/family-notify/probe")
                           .cookie(cookie)
                           .param("_csrf", token.group(1)))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
        verify(brevo).send(any());
    }
}
