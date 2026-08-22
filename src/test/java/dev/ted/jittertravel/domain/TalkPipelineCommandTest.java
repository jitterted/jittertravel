package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * The five commands on the speaking axis, kept in one class because they are one state machine over
 * one context: what each refuses is only meaningful next to what its siblings allow.
 * <p>
 * Every refusal here is about a fact that would be <strong>untrue</strong> — accepting a talk
 * nobody was sent, submitting to a conference with no CFP. None of them is about ordering or
 * duplication: this app records what already happened in the world, often late, so a command that
 * merely arrives out of sequence is allowed.
 */
class TalkPipelineCommandTest {

    private static final Instant RECORDED_ON = Instant.parse("2026-08-22T10:15:00Z");

    private static TalkPipelineContext live(SpeakingStatus status) {
        return new TalkPipelineContext(true, status, ConferenceFormat.CALL_FOR_PAPERS);
    }

    private static TalkPipelineContext gone() {
        return new TalkPipelineContext(false, SpeakingStatus.NOT_SPEAKING, null);
    }

    @Nested
    class SubmitTalk {

        @Test
        void emitsTalkSubmitted() {
            ConferenceId conferenceId = ConferenceId.random();

            List<TalkSubmitted> events =
                    new SubmitTalkCommand(conferenceId, RECORDED_ON)
                            .execute(live(SpeakingStatus.NOT_SPEAKING))
                            .toList();

            assertThat(events)
                    .containsExactly(new TalkSubmitted(conferenceId, RECORDED_ON));
        }

        @Test
        void unknownConferenceIsRejected() {
            assertThatExceptionOfType(ConferenceNotFound.class)
                    .isThrownBy(() -> new SubmitTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(gone())
                            .toList());
        }

        /** No call for papers exists, so there is nothing to submit to. */
        @Test
        void anOpenSpaceConferenceIsRejected() {
            TalkPipelineContext openSpace = new TalkPipelineContext(
                    true, SpeakingStatus.NOT_SPEAKING, ConferenceFormat.OPEN_SPACE);

            assertThatExceptionOfType(ConferenceHasNoCfp.class)
                    .isThrownBy(() -> new SubmitTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(openSpace)
                            .toList())
                    .withMessageContaining("no CFP to submit to");
        }

        /**
         * The one refusal that protects the fold rather than the truth: {@link SpeakingStatus}
         * takes the last event, so a submission recorded after an acceptance would quietly
         * un-accept the talk that is already in the program.
         */
        @Test
        void submittingAfterAnAcceptanceIsRejected() {
            assertThatExceptionOfType(TalkAlreadyAccepted.class)
                    .isThrownBy(() -> new SubmitTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.ACCEPTED))
                            .toList());
        }

        /** A second proposal to the same CFP, and re-submitting after pulling one, are ordinary. */
        @Test
        void submittingAgainIsAllowedFromEveryOtherState() {
            assertThatNoException().isThrownBy(() -> {
                new SubmitTalkCommand(ConferenceId.random(), RECORDED_ON)
                        .execute(live(SpeakingStatus.SUBMITTED)).toList();
                new SubmitTalkCommand(ConferenceId.random(), RECORDED_ON)
                        .execute(live(SpeakingStatus.WITHDRAWN)).toList();
                new SubmitTalkCommand(ConferenceId.random(), RECORDED_ON)
                        .execute(live(SpeakingStatus.REJECTED)).toList();
            });
        }
    }

    @Nested
    class AcceptTalk {

        @Test
        void emitsTalkAccepted() {
            ConferenceId conferenceId = ConferenceId.random();

            List<TalkAccepted> events =
                    new AcceptTalkCommand(conferenceId, RECORDED_ON)
                            .execute(live(SpeakingStatus.SUBMITTED))
                            .toList();

            assertThat(events)
                    .containsExactly(new TalkAccepted(conferenceId, RECORDED_ON));
        }

        /**
         * Attendance is committed by <em>folding</em> the acceptance, not by a second event — so
         * this command emits exactly one, and nothing here says anything about going.
         */
        @Test
        void emitsNoAttendanceEventOfItsOwn() {
            List<TalkAccepted> events =
                    new AcceptTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.SUBMITTED))
                            .toList();

            assertThat(events).hasSize(1);
        }

        @Test
        void unknownConferenceIsRejected() {
            assertThatExceptionOfType(ConferenceNotFound.class)
                    .isThrownBy(() -> new AcceptTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(gone())
                            .toList());
        }

        @Test
        void acceptingWhenNothingWasSubmittedIsRejected() {
            assertThatExceptionOfType(NoTalkToDecide.class)
                    .isThrownBy(() -> new AcceptTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.NOT_SPEAKING))
                            .toList());
        }

        /**
         * An invitation is not a submission: organizers who invited Ted have nothing of his to
         * accept. Saying yes to an invitation is a confirmation on the attendance axis.
         */
        @Test
        void anInvitationCannotBeAcceptedThroughTheSubmissionAxis() {
            assertThatExceptionOfType(NoTalkToDecide.class)
                    .isThrownBy(() -> new AcceptTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.INVITED))
                            .toList());
        }

        /** "Someone dropped out, can you still do it?" — real, and the last event wins. */
        @Test
        void acceptingAfterARejectionIsAllowed() {
            assertThatNoException()
                    .isThrownBy(() -> new AcceptTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.REJECTED))
                            .toList());
        }
    }

    @Nested
    class RejectTalk {

        @Test
        void emitsTalkRejected() {
            ConferenceId conferenceId = ConferenceId.random();

            List<TalkRejected> events =
                    new RejectTalkCommand(conferenceId, RECORDED_ON)
                            .execute(live(SpeakingStatus.SUBMITTED))
                            .toList();

            assertThat(events)
                    .containsExactly(new TalkRejected(conferenceId, RECORDED_ON));
        }

        @Test
        void unknownConferenceIsRejected() {
            assertThatExceptionOfType(ConferenceNotFound.class)
                    .isThrownBy(() -> new RejectTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(gone())
                            .toList());
        }

        @Test
        void rejectingWhenNothingWasSubmittedIsRejected() {
            assertThatExceptionOfType(NoTalkToDecide.class)
                    .isThrownBy(() -> new RejectTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.NOT_SPEAKING))
                            .toList());
        }

        /**
         * What a rejection <em>costs</em> — a dropped conference where acceptance was required, a
         * decision to make where it was not — is a fold over this event and the format, in the read
         * models. The command emits the one fact and removes nothing.
         */
        @Test
        void emitsTheSameFactWhateverTheFormat() {
            ConferenceId conferenceId = ConferenceId.random();
            TalkPipelineContext acceptanceRequired = new TalkPipelineContext(
                    true, SpeakingStatus.SUBMITTED, ConferenceFormat.ACCEPTANCE_REQUIRED);

            List<TalkRejected> events = new RejectTalkCommand(conferenceId, RECORDED_ON)
                    .execute(acceptanceRequired)
                    .toList();

            assertThat(events)
                    .containsExactly(new TalkRejected(conferenceId, RECORDED_ON));
        }
    }

    @Nested
    class WithdrawTalk {

        @Test
        void emitsTalkWithdrawn() {
            ConferenceId conferenceId = ConferenceId.random();

            List<TalkWithdrawn> events =
                    new WithdrawTalkCommand(conferenceId, RECORDED_ON)
                            .execute(live(SpeakingStatus.SUBMITTED))
                            .toList();

            assertThat(events)
                    .containsExactly(new TalkWithdrawn(conferenceId, RECORDED_ON));
        }

        /** The case the command mostly exists for: a schedule clash after the good news. */
        @Test
        void withdrawingAnAcceptedTalkIsAllowed() {
            assertThatNoException()
                    .isThrownBy(() -> new WithdrawTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.ACCEPTED))
                            .toList());
        }

        @Test
        void unknownConferenceIsRejected() {
            assertThatExceptionOfType(ConferenceNotFound.class)
                    .isThrownBy(() -> new WithdrawTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(gone())
                            .toList());
        }

        @Test
        void withdrawingWithNothingOutstandingIsRejected() {
            assertThatExceptionOfType(NoTalkToWithdraw.class)
                    .isThrownBy(() -> new WithdrawTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.NOT_SPEAKING))
                            .toList());
            assertThatExceptionOfType(NoTalkToWithdraw.class)
                    .isThrownBy(() -> new WithdrawTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.WITHDRAWN))
                            .toList());
            assertThatExceptionOfType(NoTalkToWithdraw.class)
                    .isThrownBy(() -> new WithdrawTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.REJECTED))
                            .toList());
        }

        /** Declining an invitation is a decision on the attendance axis, not a withdrawal. */
        @Test
        void anInvitationCannotBeWithdrawn() {
            assertThatExceptionOfType(NoTalkToWithdraw.class)
                    .isThrownBy(() -> new WithdrawTalkCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.INVITED))
                            .toList());
        }
    }

    @Nested
    class InviteToSpeak {

        @Test
        void emitsInvitedToSpeak() {
            ConferenceId conferenceId = ConferenceId.random();

            List<InvitedToSpeak> events =
                    new InviteToSpeakCommand(conferenceId, RECORDED_ON)
                            .execute(live(SpeakingStatus.NOT_SPEAKING))
                            .toList();

            assertThat(events)
                    .containsExactly(new InvitedToSpeak(conferenceId, RECORDED_ON));
        }

        @Test
        void unknownConferenceIsRejected() {
            assertThatExceptionOfType(ConferenceNotFound.class)
                    .isThrownBy(() -> new InviteToSpeakCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(gone())
                            .toList());
        }

        /**
         * An open-space conference has no CFP to submit to, but its organizers can still ask for a
         * keynote — so unlike {@link SubmitTalkCommand} this one does not consult the format.
         */
        @Test
        void anOpenSpaceConferenceCanStillInvite() {
            TalkPipelineContext openSpace = new TalkPipelineContext(
                    true, SpeakingStatus.NOT_SPEAKING, ConferenceFormat.OPEN_SPACE);

            assertThatNoException()
                    .isThrownBy(() -> new InviteToSpeakCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(openSpace)
                            .toList());
        }

        /** Turned down through the CFP, then asked directly. The last event wins. */
        @Test
        void invitingAfterARejectionIsAllowed() {
            assertThatNoException()
                    .isThrownBy(() -> new InviteToSpeakCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.REJECTED))
                            .toList());
        }

        /**
         * An invitation is an offer, so it commits nothing: no attendance event rides along, and
         * saying yes stays a separate, explicit act.
         */
        @Test
        void emitsNoAttendanceEventOfItsOwn() {
            List<InvitedToSpeak> events =
                    new InviteToSpeakCommand(ConferenceId.random(), RECORDED_ON)
                            .execute(live(SpeakingStatus.NOT_SPEAKING))
                            .toList();

            assertThat(events).hasSize(1);
        }
    }
}
