package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Records that the organizers turned Ted's talk down, emitting {@link TalkRejected}.
 * <p>
 * Refused when no talk was ever submitted, for the reason {@link AcceptTalkCommand} gives: there
 * is nothing to turn down. An unanswered invitation is not a submission either — declining one is
 * {@link DeclineConferenceCommand} on the attendance axis, and it is a different fact (Ted said no,
 * not them).
 * <p>
 * <strong>The command does not know what a rejection costs.</strong> For an
 * {@code ACCEPTANCE_REQUIRED} conference this drops the conference entirely, and for a
 * {@code CALL_FOR_PAPERS} one it leaves a decision to make — but that branch is a fold over this
 * event and the format, and it lives in the read models. Nothing is removed here, and no second
 * event is emitted: a dropped conference is <em>derived</em>, so it comes back if the rejection is
 * ever superseded.
 */
public record RejectTalkCommand(
        ConferenceId conferenceId,
        Instant decidedOn
) implements DomainCommand<TalkPipelineContext> {

    @Override
    public Stream<TalkRejected> execute(TalkPipelineContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound(
                    "No conference found to reject a talk for: " + conferenceId);
        }
        if (context.speakingStatus() == SpeakingStatus.NOT_SPEAKING
            || context.speakingStatus() == SpeakingStatus.INVITED) {
            throw new NoTalkToDecide(
                    "No talk was submitted to this conference, so none can be rejected: "
                    + conferenceId);
        }
        return Stream.of(new TalkRejected(conferenceId, decidedOn));
    }
}
