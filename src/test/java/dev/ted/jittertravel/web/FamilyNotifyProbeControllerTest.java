package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The family-email probe on {@code /admin}: a wire check that sends one fixed message through the
 * real client, says where it went, and writes nothing. The kill switch is off in this slice (its
 * default), which is the case that matters: the probe has to work before the switch is flipped.
 */
@Tag("spring")
@WebMvcTest(AdminController.class)
@WithMockUser(roles = "OWNER")
class FamilyNotifyProbeControllerTest {

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
    LegacyEventMigration legacyEventMigration;
    @MockitoBean
    BrevoEmailClient brevo;

    private void configured() {
        given(brevo.configured()).willReturn(true);
        given(brevo.recipient()).willReturn("family@example.com");
    }

    @Test
    void theButtonNamesTheRecipientBeforeAnythingIsClicked() {
        configured();

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .contains("<button type=\"submit\" class=\"probe-button\">Send a test email to "
                          + "<span>family@example.com</span></button>")
                .contains("action=\"/admin/family-notify/probe\"");
    }

    @Test
    void anUnconfiguredNotifierOffersNoButtonAndSaysWhatToSet() {
        given(brevo.configured()).willReturn(false);

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .contains("Family email is not configured.")
                .contains("Set BREVO_API_KEY and FAMILY_NOTIFY_EMAIL on the app service, then redeploy.")
                .doesNotContain("class=\"probe-button\"");
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
    void theFlashSaysWhichAddressItWentTo() {
        configured();

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatus3xxRedirection()
                .flash()
                .containsEntry("familyProbeMessage",
                        "Test email accepted by Brevo for family@example.com. Check that inbox, and spam.");
    }

    @Test
    void aFailedSendRendersTheErrorOnTheAdminPageRatherThanA500() {
        configured();
        willThrow(new RestClientResponseException("502 Bad Gateway", 502, "Bad Gateway", null, null, null))
                .given(brevo).send(any());

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<p class=\"probe-error\">Could not send the test email to family@example.com: "
                          + "502 Bad Gateway</p>")
                .contains("<title>Admin");
    }

    @Test
    void anUnconfiguredProbeSendsNothingAndSaysSo() {
        given(brevo.configured()).willReturn(false);

        assertThat(mockMvc.post().uri("/admin/family-notify/probe").with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<p class=\"probe-error\">Not sent: there is no API key or no recipient "
                          + "configured.</p>");

        verify(brevo, never()).send(any());
    }
}
