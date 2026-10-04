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
 * The family-email test on {@code /admin}, in the setup checklist's left column: the approved
 * mockup (https://claude.ai/artifact/Xu3i63jGMKTdCbWKVqedxA, placement X) as a rendered page. The
 * wording of every state is asserted in {@code FamilyEmailSetupTest}; what is asserted here is that
 * the controller feeds it the right facts and the template draws them. The kill switch is off in this
 * slice (its default), which is the case that matters: the test has to work before the switch is
 * flipped.
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
    void theChecklistSitsInALeftColumnBesideThePage() {
        configured();

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"layout\">")
                .contains("aria-label=\"Setup checklist\"")
                .contains("<span class=\"setup-title\">Setting up: family email</span>")
                .contains("<span class=\"setup-count\">1 of 3 done</span>");
    }

    @Test
    void theButtonNamesTheRecipientBeforeAnythingIsClickedAndPostsToTheProbe() {
        configured();

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .contains("<button type=\"submit\" class=\"setup-button\">Send a test email to ted@example.com</button>")
                .containsPattern("<form action=\"/admin/family-notify/probe\" method=\"post\">\\s*<input type=\"hidden\" name=\"_csrf\"");
    }

    @Test
    void theButtonIsFilledWithAnAccentThisPageActuallyDefines() {
        configured();

        assertThat(mockMvc.get().uri("/admin"))
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

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"setup-what\">Key and recipient are not set</div>")
                .contains("<b>Next:</b> <span>Set the variables JITTERTRAVEL_BREVO_API_KEY and FAMILY_NOTIFY_EMAIL "
                          + "on the app service, then redeploy.</span>")
                .doesNotContain("class=\"setup-button\"");
    }

    @Test
    void aRememberedSuccessShowsInTheViewersZoneFromTheirCookie() {
        configured();
        given(memory.last()).willReturn(Optional.of(new FamilyTestMemory.Test(NOW, true, "")));

        assertThat(mockMvc.get().uri("/admin").cookie(new Cookie("viewerZone", "America/Los_Angeles")))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"setup-what\">Test email sent at 3:42 PM PDT</div>")
                .contains("<button type=\"submit\" class=\"setup-button secondary\">Send another</button>")
                .contains("<span class=\"setup-count\">2 of 3 done</span>")
                .doesNotContain("setup-row ok flash");
    }

    @Test
    void theProbeSendsTheFixedMessageToTheConfiguredRecipientEvenWithTheKillSwitchOff() {
        configured();

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(brevo).send(argThat((FamilyMessage message) ->
                message.subject().equals("JitterTravel test email")
                && message.textContent().contains("Nothing was booked and nothing needs doing")));
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

        assertThat(mockMvc.get().uri("/admin").flashAttr("justTested", true))
                .hasStatusOk()
                .bodyText()
                .contains("class=\"setup-row ok flash\"");
    }

    @Test
    void aFailedSendIsRememberedAndRendersAmberOnTheAdminPageRatherThanA500() {
        configured();
        willThrow(new RestClientResponseException("502 Bad Gateway", 502, "Bad Gateway", null, null, null))
                .given(brevo).send(any());
        given(memory.last()).willReturn(Optional.of(
                new FamilyTestMemory.Test(NOW, false, "The send failed: 502 Bad Gateway")));

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("class=\"setup-row warn flash\"")
                .contains("<div class=\"setup-what\">Test email failed</div>")
                .contains("The send failed: 502 Bad Gateway Nothing was delivered.")
                .contains("<button type=\"submit\" class=\"setup-button\">Try again</button>");

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
