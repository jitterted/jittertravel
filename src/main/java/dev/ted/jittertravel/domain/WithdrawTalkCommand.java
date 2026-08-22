package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Records that Ted pulled his talk, emitting {@link TalkWithdrawn}.
 * <p>
 * Legal from {@link SpeakingStatus#SUBMITTED} (pulled it before they decided) and from
 * {@link SpeakingStatus#ACCEPTED} (a clash after the good news — the case this command mostly
 * exists for). Refused where nothing is outstanding to withdraw: nothing submitted, already
 * withdrawn, already turned down, or only invited — declining an invitation is
 * {@link DeclineConferenceCommand}, on the other axis.
 * <p>
 * <strong>Withdrawing says nothing about attending.</strong> No attendance event is emitted and
 * none is implied: a conference Ted committed to stays committed and simply stops being one he
 * speaks at. Not going is {@link DeclineConferenceCommand}, recorded separately.
 */
public record WithdrawTalkCommand(
        ConferenceId conferenceId,
        Instant withdrawnOn
) implements DomainCommand<TalkPipelineContext> {

    @Override
    public Stream<TalkWithdrawn> execute(TalkPipelineContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound(
                    "No conference found to withdraw a talk from: " + conferenceId);
        }
        if (context.speakingStatus() != SpeakingStatus.SUBMITTED
            && context.speakingStatus() != SpeakingStatus.ACCEPTED) {
            throw new NoTalkToWithdraw(
                    "No outstanding talk to withdraw from this conference: " + conferenceId);
        }
        return Stream.of(new TalkWithdrawn(conferenceId, withdrawnOn));
    }
}
