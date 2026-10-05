package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The family-email test email, a button in the "Last test email" row of {@code /admin/settings}
 * (mockup https://claude.ai/artifact/KZDEqfXHxftbMiWwEnofYF; it replaced a checklist in the left
 * column of {@code /admin}). What each state says is {@code SettingsReporterTest}'s claim; asserted
 * here is that the controller feeds the reporter the right facts, the template draws the button, and
 * a click goes where the mockup says. The kill switch is off in this class (its default), which is
 * the case that matters: the test has to work before the switch is flipped.
 */
@Tag("spring")
@WebMvcTest(AdminController.class)
@WithMockUser(roles = "OWNER")
class FamilyNotifyProbeControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-05T22:42:00Z");

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
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
    LegacyEventMigration legacyEventMigration;
    @MockitoBean
    BrevoEmailClient brevo;
    @MockitoBean
    FamilyTestMemory memory;

    private void configured() {
        given(brevo.configured()).willReturn(true);
        given(brevo.recipient()).willReturn("ted@example.com");
    }

    @Test
    void theAdminHomeHasNoSetupColumnAndNoTestButton() {
        configured();

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"layout\">")
                .doesNotContain("Setup checklist")
                .doesNotContain("setup-button")
                .doesNotContain("/admin/family-notify/probe");
    }

    @Test
    void theButtonNamesTheRecipientBeforeAnythingIsClickedAndPostsToTheProbe() {
        configured();

        assertThat(mockMvc.get().uri("/admin/settings"))
                .hasStatusOk()
                .bodyText()
                .contains("<button type=\"submit\" class=\"row-button\">Send a test email to ted@example.com</button>")
                .containsPattern("<form class=\"row-form\" action=\"/admin/family-notify/probe\" method=\"post\">\\s*<input type=\"hidden\" name=\"_csrf\"");
    }

    @Test
    void theButtonIsFilledWithAnAccentThisPageActuallyDefines() {
        configured();

        assertThat(mockMvc.get().uri("/admin/settings"))
                .hasStatusOk()
                .bodyText()
                .as("this page does not load site.css, so a colour it borrows from there is not defined")
                .contains("--accent: #4f46e5;")
                .contains("background: var(--accent);")
                .doesNotContain("var(--accent-color)");
    }

    @Test
    void anUnconfiguredNotifierOffersNoButtonAndSaysWhatToSet() {
        given(brevo.configured()).willReturn(false);

        assertThat(mockMvc.get().uri("/admin/settings"))
                .hasStatusOk()
                .bodyText()
                .contains("Cannot send until the Brevo key and the family address are set.")
                .doesNotContain("class=\"row-button\"");
    }

    @Test
    void aRememberedSuccessShowsInTheViewersZoneFromTheirCookieWithAQuieterButton() {
        configured();
        given(memory.last()).willReturn(Optional.of(new FamilyTestMemory.Test(NOW, true, "")));

        assertThat(mockMvc.get().uri("/admin/settings").cookie(new Cookie("viewerZone", "America/Los_Angeles")))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"mono\">3:42 PM PDT, to ted@example.com</span>")
                .contains("<button type=\"submit\" class=\"row-button secondary\">Send another</button>")
                .doesNotContain("setting ok with-button flash");
    }

    @Test
    void theProbeSendsTheFixedMessageToTheConfiguredRecipientEvenWithTheKillSwitchOff() {
        configured();

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin/settings");

        verify(brevo).send(argThat((FamilyMessage message) ->
                message.subject().equals("JitterTravel test email")
                && message.textContent().contains("Nothing was booked, and nothing needs doing")));
    }

    @Test
    void aSuccessIsRememberedAndTheNextPageFlashesItsResult() {
        configured();

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatus3xxRedirection()
                .flash()
                .containsEntry("justTested", true);

        verify(memory).succeeded(NOW);
    }

    @Test
    void theResultRowFlashesOnlyOnThePageThatFollowsTheClick() {
        configured();
        given(memory.last()).willReturn(Optional.of(new FamilyTestMemory.Test(NOW, true, "")));

        assertThat(mockMvc.get().uri("/admin/settings").flashAttr("justTested", true))
                .hasStatusOk()
                .bodyText()
                .contains("class=\"setting ok with-button flash\"");
    }

    @Test
    void aFailedSendIsRememberedAndRendersAmberOnTheSettingsPageRatherThanA500() {
        configured();
        willThrow(new RestClientResponseException("502 Bad Gateway", 502, "Bad Gateway", null, null, null))
                .given(brevo).send(any());
        given(memory.last()).willReturn(Optional.of(
                new FamilyTestMemory.Test(NOW, false, "The send failed: 502 Bad Gateway")));

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("class=\"setting warn with-button flash\"")
                .contains("<span class=\"what\">The send failed: 502 Bad Gateway Nothing was delivered.</span>")
                .contains("<button type=\"submit\" class=\"row-button\">Try again</button>");

        verify(memory).failed(NOW, "The send failed: 502 Bad Gateway");
        verify(memory, never()).succeeded(any());
    }

    @Test
    void anUnconfiguredProbeSendsNothingAndRemembersNothing() {
        given(brevo.configured()).willReturn(false);

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatusOk();

        verify(brevo, never()).send(any());
        verify(memory, never()).succeeded(any());
        verify(memory, never()).failed(any(), any());
    }
}
