package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.DeclineConference;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(DeclineConferenceController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class DeclineConferenceControllerTest {

    private static final ZoneId VENUE_ZONE = ZoneId.of("Africa/Casablanca");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    DeclineConference declineConference;

    @MockitoBean
    ConferenceProjector projector;

    private static ConferenceView viewFor(UUID conferenceId) {
        return new ConferenceView(
                ConferenceId.of(conferenceId),
                "Devoxx Morocco",
                "Palais des Congrès",
                new Address("Avenue de France", "Marrakesh", "", "40000", "Morocco", "Marrakesh"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 7, 9, 0), VENUE_ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 9, 17, 0), VENUE_ZONE),
                AttendanceCommitment.WATCHING, false, null, ConferenceFormat.CALL_FOR_PAPERS);
    }

    @Test
    void getRendersDeclineConfirmationPageForKnownConference() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(any())).willReturn(Optional.of(viewFor(conferenceId)));

        assertThat(mockMvc.get().uri("/conferences/" + conferenceId + "/decline"))
                .hasStatusOk()
                .bodyText()
                .contains("Devoxx Morocco")
                .contains("Marrakesh, Morocco");
    }

    @Test
    void getOnUnknownConferenceRedirectsToList() {
        given(projector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/conferences/" + UUID.randomUUID() + "/decline"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void decliningRedirectsBackToTheList() {
        assertThat(mockMvc.post().uri("/conferences/" + UUID.randomUUID() + "/decline")
                .with(csrf())
                .param("reason", "Schedule clash"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void requestCarriesThePathIdAndTheReason() {
        UUID conferenceId = UUID.randomUUID();

        mockMvc.post().uri("/conferences/" + conferenceId + "/decline")
                .with(csrf())
                .param("reason", "Schedule clash")
                .exchange();

        // declinedOn is the injected Clock's instant (WebTodayTestConfig's fixed one), never an
        // ambient read.
        then(declineConference).should().declineConference(any(),
                eq(new DeclineConferenceRequest(conferenceId, "Schedule clash")),
                eq(WebTodayTestConfig.FIXED_INSTANT));
    }

    @Test
    void omittedReasonBecomesEmptyRatherThanNull() {
        UUID conferenceId = UUID.randomUUID();

        mockMvc.post().uri("/conferences/" + conferenceId + "/decline")
                .with(csrf())
                .exchange();

        then(declineConference).should().declineConference(any(),
                eq(new DeclineConferenceRequest(conferenceId, "")), any());
    }

    @Test
    void alreadyGoneConferenceRedirectsToListInsteadOfThrowing() {
        willThrow(new ConferenceNotFound("No conference found to decline"))
                .given(declineConference).declineConference(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/" + UUID.randomUUID() + "/decline")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void malformedConferenceIdRedirectsInsteadOfThrowing() {
        assertThat(mockMvc.post().uri("/conferences/not-a-uuid/decline")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");

        then(declineConference).shouldHaveNoInteractions();
    }
}
