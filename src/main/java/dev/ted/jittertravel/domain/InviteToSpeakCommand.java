package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Records that the organizers asked Ted to speak, emitting {@link InvitedToSpeak}.
 * <p>
 * The only refusal is a conference that does not exist. In particular the {@link ConferenceFormat}
 * is <em>not</em> consulted: an open-space conference has no CFP to submit to, but its organizers
 * can still invite a keynote, and an invitation to a call-for-papers conference is just as real as
 * one without.
 * <p>
 * Recording an invitation over a rejection is allowed and is a genuine sequence — turned down
 * through the CFP, then asked directly. The last event wins.
 * <p>
 * <strong>It commits nothing.</strong> An invitation is an offer awaiting Ted's yes, which is a
 * separate {@link ConfirmConferenceAttendanceCommand} with
 * {@link AttendanceBasis#SPEAKING_INVITED}. Contrast {@link AcceptTalkCommand}, which completes a
 * decision Ted already made by submitting. That difference is why an unanswered invitation stays
 * off the public calendar's speaking badge.
 */
public record InviteToSpeakCommand(
        ConferenceId conferenceId,
        Instant invitedOn
) implements DomainCommand<TalkPipelineContext> {

    @Override
    public Stream<InvitedToSpeak> execute(TalkPipelineContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound(
                    "No conference found to record a speaking invitation for: " + conferenceId);
        }
        return Stream.of(new InvitedToSpeak(conferenceId, invitedOn));
    }
}
