package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.TalkTracking;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.NoTalkToDecide;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The page that records a talk's progress. A Thymeleaf endpoint, so it needs a slice test at all —
 * template errors only surface at render time.
 */
@WebMvcTest(RecordTalkController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class RecordTalkControllerTest {

    private static final ZoneId VENUE_ZONE = ZoneId.of("Europe/Amsterdam");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    TalkTracking talkTracking;

    @MockitoBean
    ConferenceProjector projector;

    private static ConferenceView viewFor(UUID conferenceId, SpeakingStatus status,
                                          ConferenceFormat format) {
        return new ConferenceView(
                ConferenceId.of(conferenceId),
                "J-Fall",
                "ReeHorst",
                new Address("Bennekomseweg 24", "Ede", "", "6717 LM", "Netherlands", "Ede"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 9, 0), VENUE_ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 18, 0), VENUE_ZONE),
                AttendanceCommitment.WATCHING, false, status, null, format);
    }

    private UUID givenConference(SpeakingStatus status, ConferenceFormat format) {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId)))
                .willReturn(Optional.of(viewFor(conferenceId, status, format)));
        return conferenceId;
    }

    @Test
    void getRendersTheFormForAKnownConference() {
        UUID conferenceId = givenConference(SpeakingStatus.NOT_SPEAKING,
                                            ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(mockMvc.get().uri("/conferences/{id}/talk", conferenceId))
                .hasStatusOk()
                .bodyText()
                .contains("<h1>Record Talk</h1>")
                .contains("J-Fall");
    }

    /**
     * Only the moves that are legal from where the conference stands are offered. Nothing has been
     * submitted here, so there is no outcome to record — but an invitation can always arrive.
     */
    @Test
    void anUntouchedConferenceOffersSubmittingAndBeingInvitedOnly() {
        UUID conferenceId = givenConference(SpeakingStatus.NOT_SPEAKING,
                                            ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(mockMvc.get().uri("/conferences/{id}/talk", conferenceId))
                .hasStatusOk()
                .bodyText()
                .contains("value=\"SUBMITTED\"")
                .contains("value=\"INVITED\"")
                .doesNotContain("value=\"ACCEPTED\"")
                .doesNotContain("value=\"REJECTED\"")
                .doesNotContain("value=\"WITHDRAWN\"");
    }

    @Test
    void aSubmittedTalkOffersItsOutcomes() {
        UUID conferenceId = givenConference(SpeakingStatus.SUBMITTED,
                                            ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(mockMvc.get().uri("/conferences/{id}/talk", conferenceId))
                .hasStatusOk()
                .bodyText()
                .contains("value=\"ACCEPTED\"")
                .contains("value=\"REJECTED\"")
                .contains("value=\"WITHDRAWN\"");
    }

    /** No call for papers to submit to — but the organizers can still ask for a keynote. */
    @Test
    void anOpenSpaceConferenceIsNotOfferedASubmission() {
        UUID conferenceId = givenConference(SpeakingStatus.NOT_SPEAKING, ConferenceFormat.OPEN_SPACE);

        assertThat(mockMvc.get().uri("/conferences/{id}/talk", conferenceId))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("value=\"SUBMITTED\"")
                .contains("value=\"INVITED\"");
    }

    /**
     * Arriving from a dashboard action, the choice is already made: the page opens with it
     * selected, so the second click is a confirmation rather than a decision.
     */
    @Test
    void theOutcomeInTheQueryStringArrivesPreselected() {
        UUID conferenceId = givenConference(SpeakingStatus.SUBMITTED,
                                            ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(mockMvc.get().uri("/conferences/{id}/talk?outcome=ACCEPTED", conferenceId))
                .hasStatusOk()
                .bodyText()
                .contains("value=\"ACCEPTED\" checked");
    }

    @Test
    void unknownConferenceNavigatesBackToTheListSilently() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(ConferenceId.of(conferenceId))).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/conferences/{id}/talk", conferenceId))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void malformedConferenceIdNavigatesBackToTheListSilently() {
        assertThat(mockMvc.get().uri("/conferences/not-a-uuid/talk"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    /**
     * Every value posts through, including ones this page would not have offered from the current
     * state — the domain is what refuses those, not the form.
     */
    @ParameterizedTest
    @EnumSource(TalkOutcome.class)
    void postRecordsTheChosenOutcome(TalkOutcome outcome) {
        UUID conferenceId = givenConference(SpeakingStatus.SUBMITTED,
                                            ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(mockMvc.post().uri("/conferences/{id}/talk", conferenceId)
                          .param("outcome", outcome.name())
                          .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");

        ArgumentCaptor<RecordTalkRequest> request = ArgumentCaptor.forClass(RecordTalkRequest.class);
        then(talkTracking).should().record(any(), request.capture(), any());
        assertThat(request.getValue())
                .isEqualTo(new RecordTalkRequest(conferenceId, outcome));
    }

    @Test
    void aMissingOutcomeRendersTheErrorOnThisPageRatherThanRedirecting() {
        // /conferences is a j2html view that cannot render a flash, so the error has to stay on
        // the page hosting the form.
        UUID conferenceId = givenConference(SpeakingStatus.SUBMITTED,
                                            ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(mockMvc.post().uri("/conferences/{id}/talk", conferenceId).with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("Choose what happened with the talk.");
        then(talkTracking).shouldHaveNoInteractions();
    }

    @Test
    void anUnrecognisedOutcomeIsTreatedAsNoChoiceAtAll() {
        UUID conferenceId = givenConference(SpeakingStatus.SUBMITTED,
                                            ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(mockMvc.post().uri("/conferences/{id}/talk", conferenceId)
                          .param("outcome", "WAITLISTED")
                          .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("Choose what happened with the talk.");
        then(talkTracking).shouldHaveNoInteractions();
    }

    /**
     * A move the domain refuses — a stale page in another tab, or a hand-edited parameter. The
     * reason belongs on the page hosting the form, not swallowed by a redirect.
     */
    @Test
    void anIllegalTransitionRendersItsReasonOnThePage() {
        UUID conferenceId = givenConference(SpeakingStatus.NOT_SPEAKING,
                                            ConferenceFormat.CALL_FOR_PAPERS);
        willThrow(new NoTalkToDecide("No talk was submitted to this conference"))
                .given(talkTracking).record(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/{id}/talk", conferenceId)
                          .param("outcome", "ACCEPTED")
                          .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("No talk was submitted to this conference");
    }

    /** Cancelled or declined in another tab between the lookup and the write. */
    @Test
    void aConferenceThatVanishedMidFlightNavigatesBackToTheList() {
        UUID conferenceId = givenConference(SpeakingStatus.SUBMITTED,
                                            ConferenceFormat.CALL_FOR_PAPERS);
        willThrow(new ConferenceNotFound("gone"))
                .given(talkTracking).record(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/{id}/talk", conferenceId)
                          .param("outcome", "ACCEPTED")
                          .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void readOnlyModeRedirectsToTheReadOnlyPage() {
        UUID conferenceId = givenConference(SpeakingStatus.SUBMITTED,
                                            ConferenceFormat.CALL_FOR_PAPERS);
        willThrow(new ReadOnlyModeException("read-only"))
                .given(talkTracking).record(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/{id}/talk", conferenceId)
                          .param("outcome", "ACCEPTED")
                          .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/read-only");
    }

    /**
     * The timestamp is when Ted recorded this, taken from the injected clock at the boundary —
     * never when the organizers decided, which the app cannot know.
     */
    @Test
    void theRecordedInstantComesFromTheInjectedClock() {
        UUID conferenceId = givenConference(SpeakingStatus.SUBMITTED,
                                            ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(mockMvc.post().uri("/conferences/{id}/talk", conferenceId)
                          .param("outcome", "ACCEPTED")
                          .with(csrf()))
                .hasStatus3xxRedirection();

        then(talkTracking).should()
                .record(any(), any(), eq(WebTodayTestConfig.FIXED_INSTANT));
    }
}
