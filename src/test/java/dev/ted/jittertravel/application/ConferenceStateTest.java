package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.SpeakingStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules the three conference read models share: how the two axes move, where they touch, and —
 * the one with teeth — when Ted counts as speaking.
 * <p>
 * That last one is a <strong>redaction</strong> rule, not a display one. The public calendar
 * publishes a speaking badge, so anything that makes {@link ConferenceState#speaking()} true
 * before Ted has committed becomes visible to a stranger. This is where that is guarded; the
 * projector repeats the check at the point of publication but cannot be reached in that state.
 */
class ConferenceStateTest {

    private static ConferenceState planned() {
        return ConferenceState.planned(ConferenceFormat.CALL_FOR_PAPERS);
    }

    @Test
    void aPlannedConferenceIsWatchedAndSaysNothingAboutSpeaking() {
        ConferenceState state = planned();

        assertThat(state.commitment()).isEqualTo(AttendanceCommitment.WATCHING);
        assertThat(state.speakingStatus()).isEqualTo(SpeakingStatus.NOT_SPEAKING);
        assertThat(state.speaking()).isFalse();
        assertThat(state.dropped()).isFalse();
    }

    /** Submitting was the opt-in, so the acceptance completes a decision Ted already made. */
    @Test
    void anAcceptanceCommitsAttendanceAndMeansSpeaking() {
        ConferenceState state = planned().submitted().accepted();

        assertThat(state.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(state.speaking()).isTrue();
    }

    /** Waiting to hear is not speaking, however hopeful. */
    @Test
    void aSubmittedTalkIsNotYetSpeaking() {
        assertThat(planned().submitted().speaking()).isFalse();
    }

    /**
     * <strong>The redaction rule.</strong> An invitation is an offer: it commits nothing, and it
     * must not count as speaking until Ted has taken it up — otherwise the public calendar
     * announces that he was asked to speak somewhere he has not decided about.
     */
    @Test
    void anUnansweredInvitationIsNotSpeaking() {
        ConferenceState state = planned().invited();

        assertThat(state.commitment())
                .as("an invitation commits nothing")
                .isEqualTo(AttendanceCommitment.WATCHING);
        assertThat(state.speaking())
                .as("and until he says yes, he is not speaking there")
                .isFalse();
    }

    @Test
    void anInvitationAcceptedAsSpeakingIsSpeaking() {
        ConferenceState state = planned().invited()
                                               .confirmed(AttendanceBasis.SPEAKING_INVITED);

        assertThat(state.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(state.speaking()).isTrue();
    }

    /** Going on a bought ticket after an invitation is attending, not speaking. */
    @Test
    void anInvitationTakenUpAsAPlainTicketIsNotSpeaking() {
        ConferenceState state = planned().invited()
                                               .confirmed(AttendanceBasis.TICKET_PURCHASED);

        assertThat(state.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(state.speaking()).isFalse();
    }

    /**
     * The fallback for conferences recorded before the submission events existed: with the stream
     * silent, the confirmation's basis is the only evidence there is.
     */
    @Test
    void withNoSubmissionEventsTheBasisDecides() {
        assertThat(planned().confirmed(AttendanceBasis.SPEAKING_ACCEPTED).speaking()).isTrue();
        assertThat(planned().confirmed(AttendanceBasis.TICKET_PURCHASED).speaking()).isFalse();
    }

    /** But once the stream has spoken it wins: the basis is a manual annotation, the events are history. */
    @Test
    void theStreamOverridesTheBasisWhenTheyDisagree() {
        ConferenceState state = planned()
                .confirmed(AttendanceBasis.SPEAKING_ACCEPTED)
                .submitted()
                .rejected();

        assertThat(state.speaking()).isFalse();
    }

    /** Pulling a talk moves one axis only. */
    @Test
    void withdrawingAnAcceptedTalkLeavesHimGoingButNotSpeaking() {
        ConferenceState state = planned().submitted().accepted().withdrawn();

        assertThat(state.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(state.speaking()).isFalse();
        assertThat(state.dropped()).isFalse();
    }

    /** The auto-drop: acceptance was the way in, so a rejection takes the conference with it. */
    @Test
    void aRejectionDropsAConferenceWhereAcceptanceWasRequired() {
        ConferenceState state =
                ConferenceState.planned(ConferenceFormat.ACCEPTANCE_REQUIRED)
                                  .submitted()
                                  .rejected();

        assertThat(state.dropped()).isTrue();
        assertThat(state.commitment()).isEqualTo(AttendanceCommitment.NOT_GOING);
    }

    /** The same event elsewhere leaves a decision to make rather than making it. */
    @Test
    void aRejectionLeavesACallForPapersConferenceWatched() {
        ConferenceState state = planned().submitted().rejected();

        assertThat(state.dropped()).isFalse();
        assertThat(state.commitment()).isEqualTo(AttendanceCommitment.WATCHING);
        assertThat(state.speakingStatus()).isEqualTo(SpeakingStatus.REJECTED);
    }

    @Test
    void decliningDropsTheConferenceWhateverTheTalkDid() {
        assertThat(planned().submitted().declined().dropped()).isTrue();
        assertThat(planned().submitted().accepted().declined().dropped()).isTrue();
    }
}
