package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.FamilyNotificationMessages;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The email preview page (mockup https://claude.ai/artifact/C7mjK5bUm6yY5nDVX1JgWu): every family
 * email as it would be sent, and a button that sends three of them to Ted and to no one else.
 */
@Tag("spring")
@WebMvcTest(EmailPreviewController.class)
@WithMockUser(roles = "OWNER")
@TestPropertySource(properties = "jittertravel.base-url=https://jittertravel.com")
class EmailPreviewControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-05T22:42:00Z");

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        FamilyNotificationMessages messages() {
            return new FamilyNotificationMessages(new StaticAirportCityResolver(), "https://jittertravel.com");
        }
    }

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    BrevoEmailClient brevo;
    @MockitoBean
    EmailPreviewMemory memory;

    private void canSend() {
        given(brevo.hasKey()).willReturn(true);
        given(brevo.replyTo()).willReturn("ted@example.com");
        given(brevo.recipient()).willReturn("family@example.com");
    }

    @Test
    void thePageShowsEveryEmailAsSentWithItsSubjectAndTheFileItComesFrom() {
        canSend();

        assertThat(mockMvc.get().uri("/admin/email-preview"))
                .hasStatusOk()
                .bodyText()
                .contains("<b>Flight booked</b><span>email/flight-booked.txt</span>")
                .contains("<b>Trip booked</b><span>email/trip-booked.txt</span>")
                .contains("<b>Trip cancelled</b><span>email/trip-cancelled.txt</span>")
                .contains("<b>Test email</b><span>email/test-email.txt</span>")
                .contains("<span>(JitterTravel) Ted booked a new flight: SFO → ORD</span>")
                .contains("<span>(JitterTravel) Ted booked a new trip: SFO → ORD → YOW → ORD → SFO</span>")
                .contains("<span>(JitterTravel) Ted cancelled a trip: SFO → ORD → YOW → ORD → SFO</span>")
                .contains("<span>JitterTravel test email</span>");
    }

    @Test
    void thePageShowsTheFourConferenceEmailsAsSentButTheSendButtonStaysFlightOnly() {
        canSend();

        assertThat(mockMvc.get().uri("/admin/email-preview"))
                .hasStatusOk()
                .bodyText()
                .contains("<b>Conference going</b><span>email/conference-going.txt</span>")
                .contains("<b>Conference declined</b><span>email/conference-not-going.txt</span>")
                .contains("<b>Conference cancelled</b><span>email/conference-not-going.txt</span>")
                .contains("<b>Conference talk rejected</b><span>email/conference-not-going.txt</span>")
                .contains("<span>(JitterTravel) Ted is going to a conference: SoCraTes 2026</span>")
                .contains("<span>(JitterTravel) Ted is no longer going to SoCraTes 2026</span>")
                .contains("Send all three to ted@example.com");
    }

    @Test
    void theCalendarLinkInAnEmailIsAClickableLinkToTheFirstFlightsDay() {
        canSend();

        assertThat(mockMvc.get().uri("/admin/email-preview"))
                .hasStatusOk()
                .bodyText()
                .contains("<a href=\"https://jittertravel.com/calendar?day=2026-10-18\">"
                          + "https://jittertravel.com/calendar?day=2026-10-18</a>");
    }

    @Test
    void thePageSaysWhichBaseUrlTheLinkUses() {
        canSend();

        assertThat(mockMvc.get().uri("/admin/email-preview"))
                .hasStatusOk()
                .bodyText()
                .contains("The calendar link uses the sample&#39;s date and your JITTERTRAVEL_BASE_URL "
                          + "(https://jittertravel.com).");
    }

    @Test
    void theReadyPanelOffersAFilledButtonNamingYourAddress() {
        canSend();

        assertThat(mockMvc.get().uri("/admin/email-preview"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"send-what\">Send these three emails to yourself</div>")
                .contains("<button type=\"submit\" class=\"send-button\">Send all three to ted@example.com</button>")
                .containsPattern("<form action=\"/admin/email-preview/send\" method=\"post\">\\s*<input type=\"hidden\" name=\"_csrf\"");
    }

    @Test
    void withNoReplyToThePanelSaysSoOffersNoButtonAndTheEmailsStillShow() {
        given(brevo.hasKey()).willReturn(true);
        given(brevo.replyTo()).willReturn("");

        assertThat(mockMvc.get().uri("/admin/email-preview"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"send-what\">Cannot send yet</div>")
                .doesNotContain("class=\"send-button\"")
                .contains("<span>(JitterTravel) Ted booked a new flight: SFO → ORD</span>");
    }

    @Test
    void aRememberedSuccessShowsInTheViewersZoneFromTheirCookie() {
        canSend();
        given(memory.last()).willReturn(Optional.of(
                new EmailPreviewMemory.Result(NOW, 3, 3, "ted@example.com", "")));

        assertThat(mockMvc.get().uri("/admin/email-preview").cookie(new Cookie("viewerZone", "America/Los_Angeles")))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"send-what\">3 emails sent to ted@example.com at 3:42 PM PDT</div>")
                .contains("<button type=\"submit\" class=\"send-button secondary\">Send them again</button>");
    }

    @Test
    void sendingGoesToTedAndOnlyToTedNeverToTheFamilyAddress() {
        canSend();

        assertThat(mockMvc.post().uri("/admin/email-preview/send").with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin/email-preview");

        ArgumentCaptor<FamilyMessage> sent = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(brevo, times(3)).sendTo(eq("ted@example.com"), sent.capture());
        verify(brevo, never()).send(any());
        assertThat(sent.getAllValues())
                .extracting(FamilyMessage::subject)
                .containsExactly(
                        "(JitterTravel) Ted booked a new flight: SFO → ORD",
                        "(JitterTravel) Ted booked a new trip: SFO → ORD → YOW → ORD → SFO",
                        "(JitterTravel) Ted cancelled a trip: SFO → ORD → YOW → ORD → SFO");
    }

    @Test
    void aSuccessIsRememberedAndTheNextPageFlashesIt() {
        canSend();

        assertThat(mockMvc.post().uri("/admin/email-preview/send").with(csrf()))
                .hasStatus3xxRedirection()
                .flash()
                .containsEntry("justSent", true);

        verify(memory).remember(new EmailPreviewMemory.Result(NOW, 3, 3, "ted@example.com", ""));
    }

    @Test
    void aFailureAfterTheFirstEmailIsRememberedWithHowFarItGotAndShownAmberOnThePage() {
        canSend();
        willDoNothing().willThrow(new IllegalStateException("401 Unauthorized"))
                .given(brevo).sendTo(any(), any());
        given(memory.last()).willReturn(Optional.of(new EmailPreviewMemory.Result(
                NOW, 1, 3, "ted@example.com", "The send failed: 401 Unauthorized")));

        assertThat(mockMvc.post().uri("/admin/email-preview/send").with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("class=\"send warn flash\"")
                .contains("<div class=\"send-what\">Sending failed after 1 of 3</div>")
                .contains("<button type=\"submit\" class=\"send-button\">Try again</button>");

        verify(memory).remember(new EmailPreviewMemory.Result(
                NOW, 1, 3, "ted@example.com", "The send failed: 401 Unauthorized"));
        verify(brevo, never()).send(any());
    }

    @Test
    void postingWhenThereIsNoAddressOfYoursSendsNothing() {
        given(brevo.hasKey()).willReturn(true);
        given(brevo.replyTo()).willReturn("");

        assertThat(mockMvc.post().uri("/admin/email-preview/send").with(csrf()))
                .hasStatusOk();

        verify(brevo, never()).sendTo(any(), any());
        verify(brevo, never()).send(any());
        verify(memory, never()).remember(any());
    }
}
