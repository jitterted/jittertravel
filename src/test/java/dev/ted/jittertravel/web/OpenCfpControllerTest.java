package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.OpenCfp;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(OpenCfpController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class OpenCfpControllerTest {

    /** Deliberately not the test JVM's UTC, so "which zone did it use" has a visible answer. */
    private static final ZoneId VENUE_ZONE = ZoneId.of("Europe/Amsterdam");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    OpenCfp openCfp;

    @MockitoBean
    ConferenceProjector projector;

    private static ConferenceView viewFor(UUID conferenceId, ZonedTimestamp cfpClosesOn) {
        return new ConferenceView(
                ConferenceId.of(conferenceId),
                "J-Fall",
                "ReeHorst",
                new Address("Bennekomseweg 24", "Ede", "", "6717 LM", "Netherlands", "Ede"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 9, 0), VENUE_ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 18, 0), VENUE_ZONE),
                AttendanceCommitment.WATCHING, false, SpeakingStatus.NOT_SPEAKING,
                cfpClosesOn, ConferenceFormat.CALL_FOR_PAPERS);
    }

    @Test
    void getRendersTheCfpFormForAKnownConference() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId)))
                .willReturn(Optional.of(viewFor(conferenceId, null)));

        assertThat(mockMvc.get().uri("/conferences/{id}/cfp", conferenceId))
                .hasStatusOk()
                .bodyText()
                .contains("J-Fall")
                // The zone the deadline will be stored in is stated, because the wall-clock typed
                // into the form means nothing without it.
                .contains("Europe/Amsterdam");
    }

    @Test
    void anAlreadyRecordedDeadlinePrefillsTheField() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId)))
                .willReturn(Optional.of(viewFor(conferenceId,
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 12, 23, 59), VENUE_ZONE))));

        assertThat(mockMvc.get().uri("/conferences/{id}/cfp", conferenceId))
                .hasStatusOk()
                .bodyText()
                .as("re-recording a moved deadline starts from the old one, not a blank field")
                .contains("2026-09-12T23:59");
    }

    @Test
    void getForAnUnknownConferenceGoesBackToTheListSilently() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId))).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/conferences/{id}/cfp", conferenceId))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void getWithAMalformedIdGoesBackToTheListSilently() {
        assertThat(mockMvc.get().uri("/conferences/{id}/cfp", "not-a-uuid"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    /**
     * The claim this whole slice rests on: the wall-clock typed into the form is paired with the
     * <em>conference's own venue zone</em>, taken from the dates {@code ConferencePlanned} already
     * resolved — not the server's zone, and not a second resolution from the address.
     */
    @Test
    void postStoresTheDeadlineInTheConferencesOwnVenueZone() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId)))
                .willReturn(Optional.of(viewFor(conferenceId, null)));

        assertThat(mockMvc.post().uri("/conferences/{id}/cfp", conferenceId)
                .param("closesOn", "2026-09-12T23:59")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");

        ArgumentCaptor<ZonedTimestamp> closesOn = ArgumentCaptor.forClass(ZonedTimestamp.class);
        ArgumentCaptor<OpenCfpRequest> request = ArgumentCaptor.forClass(OpenCfpRequest.class);
        then(openCfp).should().openCfp(any(), request.capture(), closesOn.capture());

        assertThat(closesOn.getValue())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 12, 23, 59), VENUE_ZONE));
        assertThat(closesOn.getValue().zone())
                .as("the venue's zone, not the server's")
                .isEqualTo(VENUE_ZONE);
        assertThat(request.getValue().conferenceId()).isEqualTo(conferenceId);
    }

    @Test
    void postForAnUnknownConferenceWritesNothingAndGoesBackToTheList() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId))).willReturn(Optional.empty());

        assertThat(mockMvc.post().uri("/conferences/{id}/cfp", conferenceId)
                .param("closesOn", "2026-09-12T23:59")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");

        then(openCfp).shouldHaveNoInteractions();
    }

    @Test
    void postWithAMalformedIdWritesNothingAndGoesBackToTheList() {
        assertThat(mockMvc.post().uri("/conferences/{id}/cfp", "not-a-uuid")
                .param("closesOn", "2026-09-12T23:59")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");

        then(openCfp).shouldHaveNoInteractions();
    }

    /** Cancelled or declined in another tab between the lookup and the write. */
    @Test
    void aConferenceThatDisappearsMidRequestGoesBackToTheListSilently() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId)))
                .willReturn(Optional.of(viewFor(conferenceId, null)));
        willThrow(new ConferenceNotFound("gone"))
                .given(openCfp).openCfp(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/{id}/cfp", conferenceId)
                .param("closesOn", "2026-09-12T23:59")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void readOnlyModeRedirectsToTheReadOnlyPage() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId)))
                .willReturn(Optional.of(viewFor(conferenceId, null)));
        willThrow(new ReadOnlyModeException("read-only"))
                .given(openCfp).openCfp(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/{id}/cfp", conferenceId)
                .param("closesOn", "2026-09-12T23:59")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/read-only");
    }
}
