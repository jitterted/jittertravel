package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ChangeConferenceDates;
import dev.ted.jittertravel.application.ConferenceDetailView;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Thymeleaf endpoint, so it needs a {@code @WebMvcTest}: a template error only surfaces at render
 * time and a renderer unit test would never see it.
 */
@Tag("spring")
@WebMvcTest(ChangeConferenceDatesController.class)
@WithMockUser(roles = "OWNER")
class ChangeConferenceDatesControllerTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ChangeConferenceDates changeConferenceDates;

    @MockitoBean
    ConferenceProjector projector;

    private static ConferenceDetailView devNexus(UUID conferenceId) {
        return devNexus(conferenceId, AttendanceCommitment.WATCHING);
    }

    private static ConferenceDetailView devNexus(UUID conferenceId, AttendanceCommitment commitment) {
        return new ConferenceDetailView(
                ConferenceId.of(conferenceId),
                "DevNexus",
                "Georgia World Congress Center",
                new Address("285 Andrew Young International Blvd NW", "Atlanta", "GA", "30313", "US", "Atlanta"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2027, 4, 5, 9, 0), NEW_YORK),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2027, 4, 7, 17, 0), NEW_YORK),
                commitment,
                null,
                false,
                SpeakingStatus.NOT_SPEAKING,
                null,
                "",
                ConferenceFormat.CALL_FOR_PAPERS,
                "");
    }

    @Test
    void getRendersTheFormPrefilledWithTheDatesInForce() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.detailById(any())).willReturn(Optional.of(devNexus(conferenceId)));

        assertThat(mockMvc.get().uri("/conferences/" + conferenceId + "/dates"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"name\">DevNexus</div>")
                .contains("value=\"2027-04-05T09:00\"")
                .contains("value=\"2027-04-07T17:00\"")
                .contains("<span>America/New_York</span>")
                .contains("action=\"/conferences/" + conferenceId + "/dates\"");
    }

    @Test
    void getForAnUnknownConferenceRedirectsToTheList() {
        given(projector.detailById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/conferences/" + UUID.randomUUID() + "/dates"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void getForAMalformedIdRedirectsToTheList() {
        assertThat(mockMvc.get().uri("/conferences/not-a-uuid/dates"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void postAppliesTheNewDatesAndReturnsToTheDetailPage() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.detailById(any())).willReturn(Optional.of(devNexus(conferenceId)));

        assertThat(mockMvc.post().uri("/conferences/" + conferenceId + "/dates")
                .param("startDate", "2027-03-29T09:00")
                .param("endDate", "2027-03-31T17:00")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences/" + conferenceId);

        ArgumentCaptor<UUID> conference = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<ChangeConferenceDatesRequest> request =
                ArgumentCaptor.forClass(ChangeConferenceDatesRequest.class);
        then(changeConferenceDates).should().changeDates(any(), conference.capture(), request.capture());
        assertThat(conference.getValue())
                .as("the id comes from the path, so a submit cannot re-target another conference")
                .isEqualTo(conferenceId);
        assertThat(request.getValue())
                .isEqualTo(new ChangeConferenceDatesRequest(
                        LocalDateTime.of(2027, 3, 29, 9, 0), LocalDateTime.of(2027, 3, 31, 17, 0)));
    }

    @Test
    void aBlankStartIsAFieldErrorAndTheServiceIsNeverCalled() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.detailById(any())).willReturn(Optional.of(devNexus(conferenceId)));

        assertThat(mockMvc.post().uri("/conferences/" + conferenceId + "/dates")
                .param("startDate", "")
                .param("endDate", "2027-03-31T17:00")
                .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"error\">Required</span>")
                .contains("<div class=\"name\">DevNexus</div>");

        then(changeConferenceDates).should(never()).changeDates(any(), any(), any());
    }

    @Test
    void anEndBeforeTheStartRendersUnderTheEndInput() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.detailById(any())).willReturn(Optional.of(devNexus(conferenceId)));
        willThrow(new InvalidDateRange("End date must be on or after start date"))
                .given(changeConferenceDates).changeDates(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/" + conferenceId + "/dates")
                .param("startDate", "2027-03-31T09:00")
                .param("endDate", "2027-03-29T17:00")
                .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"error\">End date must be on or after start date</span>")
                .as("the rejected values come back, not the ones in force")
                .contains("value=\"2027-03-29T17:00\"");
    }

    @Test
    void aConferenceGoneInAnotherTabIsSaidOnTheFormWithTheTypedDatesKept() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.detailById(any())).willReturn(Optional.of(devNexus(conferenceId)));
        willThrow(new ConferenceNotFound("gone"))
                .given(changeConferenceDates).changeDates(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/" + conferenceId + "/dates")
                .param("startDate", "2027-03-29T09:00")
                .param("endDate", "2027-03-31T17:00")
                .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<p class=\"error\">Not saved: this conference was cancelled or declined</p>")
                .as("the typed dates come back, so nothing reads as saved")
                .contains("value=\"2027-03-29T09:00\"")
                .contains("value=\"2027-03-31T17:00\"");
    }

    @Test
    void getWhileReadOnlyRedirectsBeforeTheFormIsShown() {
        given(changeConferenceDates.isReadOnly()).willReturn(true);
        given(projector.detailById(any())).willReturn(Optional.of(devNexus(UUID.randomUUID())));

        assertThat(mockMvc.get().uri("/conferences/" + UUID.randomUUID() + "/dates"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/read-only");
    }

    @Test
    void postWhileReadOnlyRedirectsWithoutCallingTheService() {
        given(changeConferenceDates.isReadOnly()).willReturn(true);
        given(projector.detailById(any())).willReturn(Optional.of(devNexus(UUID.randomUUID())));

        assertThat(mockMvc.post().uri("/conferences/" + UUID.randomUUID() + "/dates")
                .param("startDate", "2027-03-29T09:00")
                .param("endDate", "2027-03-31T17:00")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/read-only");

        then(changeConferenceDates).should(never()).changeDates(any(), any(), any());
    }

    @Test
    void getForADroppedConferenceRedirectsToItsDetailPage() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.detailById(any()))
                .willReturn(Optional.of(devNexus(conferenceId, AttendanceCommitment.NOT_GOING)));

        assertThat(mockMvc.get().uri("/conferences/" + conferenceId + "/dates"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences/" + conferenceId);
    }

    @Test
    void postForADroppedConferenceRedirectsWithoutCallingTheService() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.detailById(any()))
                .willReturn(Optional.of(devNexus(conferenceId, AttendanceCommitment.NOT_GOING)));

        assertThat(mockMvc.post().uri("/conferences/" + conferenceId + "/dates")
                .param("startDate", "2027-03-29T09:00")
                .param("endDate", "2027-03-31T17:00")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences/" + conferenceId);

        then(changeConferenceDates).should(never()).changeDates(any(), any(), any());
    }

    @Test
    void postForAnUnknownConferenceRedirectsWithoutCallingTheService() {
        given(projector.detailById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.post().uri("/conferences/" + UUID.randomUUID() + "/dates")
                .param("startDate", "2027-03-29T09:00")
                .param("endDate", "2027-03-31T17:00")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");

        then(changeConferenceDates).should(never()).changeDates(any(), any(), any());
    }
}
