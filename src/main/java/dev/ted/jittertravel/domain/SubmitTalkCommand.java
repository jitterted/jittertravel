package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Records that Ted submitted a talk to a conference's CFP, emitting {@link TalkSubmitted}.
 * <p>
 * Three refusals, each about a fact that would be <em>untrue</em> rather than merely out of order:
 * a conference that does not exist (never planned, cancelled by the organizers, or declined); an
 * {@code OPEN_SPACE} conference, which has no call for papers to submit to; and a conference whose
 * talk has already been accepted, where there is nothing left to submit.
 * <p>
 * That last one also protects the fold: {@link SpeakingStatus} takes the last event, so a
 * submission recorded after an acceptance would quietly un-accept the talk.
 * <p>
 * <strong>Submitting again is otherwise allowed.</strong> A second proposal to the same CFP is
 * ordinary, and so is re-submitting after withdrawing — both land as another {@code TalkSubmitted}
 * on a conference-keyed stream that does not count proposals.
 * <p>
 * There is deliberately <strong>no time gate</strong> against the CFP deadline, for the reason
 * {@link OpenCfpCommand} gives: this app records what already happened in the world, and a
 * submission entered a week late is still a true fact about a CFP that has since closed.
 */
public record SubmitTalkCommand(
        ConferenceId conferenceId,
        Instant submittedOn
) implements DomainCommand<TalkPipelineContext> {

    @Override
    public Stream<TalkSubmitted> execute(TalkPipelineContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound("No conference found to submit a talk to: " + conferenceId);
        }
        if (context.format() == ConferenceFormat.OPEN_SPACE) {
            throw new ConferenceHasNoCfp(
                    "An open-space conference has no CFP to submit to: " + conferenceId);
        }
        if (context.speakingStatus() == SpeakingStatus.ACCEPTED) {
            throw new TalkAlreadyAccepted(
                    "A talk has already been accepted for this conference: " + conferenceId);
        }
        return Stream.of(new TalkSubmitted(conferenceId, submittedOn));
    }
}
