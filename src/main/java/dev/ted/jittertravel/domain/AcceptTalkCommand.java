package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Records that the organizers accepted Ted's talk, emitting {@link TalkAccepted}.
 * <p>
 * <strong>On the name:</strong> commands are the imperative of the event they produce, and the
 * organizers are the ones who did it — Ted is recording a fact from the world. Same reading as
 * {@link OpenCfpCommand}.
 * <p>
 * Refused when no talk was ever submitted: organizers can only accept something they were sent, so
 * such an event would be untrue rather than merely redundant. An <em>invitation</em> is not a
 * submission and is not accepted through here — saying yes to one is
 * {@link ConfirmConferenceAttendanceCommand} on the attendance axis.
 * <p>
 * Recording an acceptance over a rejection or a withdrawal is allowed: organizers do come back
 * ("someone dropped out, can you still do it?"), and the last event wins.
 * <p>
 * <strong>This commits attendance</strong>, but not by emitting a second event — the read models
 * derive {@code GOING} from the acceptance itself. See {@link TalkAccepted}.
 */
public record AcceptTalkCommand(
        ConferenceId conferenceId,
        Instant decidedOn
) implements DomainCommand<TalkPipelineContext> {

    @Override
    public Stream<TalkAccepted> execute(TalkPipelineContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound(
                    "No conference found to accept a talk for: " + conferenceId);
        }
        if (context.speakingStatus() == SpeakingStatus.NOT_SPEAKING
            || context.speakingStatus() == SpeakingStatus.INVITED) {
            throw new NoTalkToDecide(
                    "No talk was submitted to this conference, so none can be accepted: "
                    + conferenceId);
        }
        return Stream.of(new TalkAccepted(conferenceId, decidedOn));
    }
}
