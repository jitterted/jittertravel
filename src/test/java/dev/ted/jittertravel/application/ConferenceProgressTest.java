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
 * publishes a speaking badge, so anything that makes {@link ConferenceProgress#speaking()} true
 * before Ted has committed becomes visible to a stranger. This is where that is guarded; the
 * projector repeats the check at the point of publication but cannot be reached in that state.
 */
class ConferenceProgressTest {

    private static ConferenceProgress planned() {
        return ConferenceProgress.planned(ConferenceFormat.CALL_FOR_PAPERS);
    }

    @Test
    void aPlannedConferenceIsWatchedAndSaysNothingAboutSpeaking() {
        ConferenceProgress progress = planned();

        assertThat(progress.commitment()).isEqualTo(AttendanceCommitment.WATCHING);
        assertThat(progress.speakingStatus()).isEqualTo(SpeakingStatus.NOT_SPEAKING);
        assertThat(progress.speaking()).isFalse();
        assertThat(progress.dropped()).isFalse();
    }

    /** Submitting was the opt-in, so the acceptance completes a decision Ted already made. */
    @Test
    void anAcceptanceCommitsAttendanceAndMeansSpeaking() {
        ConferenceProgress progress = planned().submitted().accepted();

        assertThat(progress.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(progress.speaking()).isTrue();
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
        ConferenceProgress progress = planned().invited();

        assertThat(progress.commitment())
                .as("an invitation commits nothing")
                .isEqualTo(AttendanceCommitment.WATCHING);
        assertThat(progress.speaking())
                .as("and until he says yes, he is not speaking there")
                .isFalse();
    }

    @Test
    void anInvitationAcceptedAsSpeakingIsSpeaking() {
        ConferenceProgress progress = planned().invited()
                                               .confirmed(AttendanceBasis.SPEAKING_INVITED);

        assertThat(progress.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(progress.speaking()).isTrue();
    }

    /** Going on a bought ticket after an invitation is attending, not speaking. */
    @Test
    void anInvitationTakenUpAsAPlainTicketIsNotSpeaking() {
        ConferenceProgress progress = planned().invited()
                                               .confirmed(AttendanceBasis.TICKET_PURCHASED);

        assertThat(progress.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(progress.speaking()).isFalse();
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
        ConferenceProgress progress = planned()
                .confirmed(AttendanceBasis.SPEAKING_ACCEPTED)
                .submitted()
                .rejected();

        assertThat(progress.speaking()).isFalse();
    }

    /** Pulling a talk moves one axis only. */
    @Test
    void withdrawingAnAcceptedTalkLeavesHimGoingButNotSpeaking() {
        ConferenceProgress progress = planned().submitted().accepted().withdrawn();

        assertThat(progress.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(progress.speaking()).isFalse();
        assertThat(progress.dropped()).isFalse();
    }

    /** The auto-drop: acceptance was the way in, so a rejection takes the conference with it. */
    @Test
    void aRejectionDropsAConferenceWhereAcceptanceWasRequired() {
        ConferenceProgress progress =
                ConferenceProgress.planned(ConferenceFormat.ACCEPTANCE_REQUIRED)
                                  .submitted()
                                  .rejected();

        assertThat(progress.dropped()).isTrue();
        assertThat(progress.commitment()).isEqualTo(AttendanceCommitment.NOT_GOING);
    }

    /** The same event elsewhere leaves a decision to make rather than making it. */
    @Test
    void aRejectionLeavesACallForPapersConferenceWatched() {
        ConferenceProgress progress = planned().submitted().rejected();

        assertThat(progress.dropped()).isFalse();
        assertThat(progress.commitment()).isEqualTo(AttendanceCommitment.WATCHING);
        assertThat(progress.speakingStatus()).isEqualTo(SpeakingStatus.REJECTED);
    }

    @Test
    void decliningDropsTheConferenceWhateverTheTalkDid() {
        assertThat(planned().submitted().declined().dropped()).isTrue();
        assertThat(planned().submitted().accepted().declined().dropped()).isTrue();
    }
}
