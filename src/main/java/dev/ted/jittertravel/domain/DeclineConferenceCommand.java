package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Records Ted's decision not to attend a planned conference, emitting {@link ConferenceAttendanceDeclined}.
 * <p>
 * The only refusal is a conference that does not exist (never planned, or already cancelled by the
 * organizers, or already declined). Like {@link CancelHotelCommand}, there is deliberately no time
 * gate: deciding not to go can happen at any point, and telling JitterTravel about it is a manual
 * step that may lag the real decision.
 */
public record DeclineConferenceCommand(
        ConferenceId conferenceId,
        String reason,
        Instant declinedOn
) implements DomainCommand<DeclineConferenceContext> {

    public DeclineConferenceCommand {
        if (reason == null) {
            reason = "";
        }
    }

    @Override
    public Stream<ConferenceAttendanceDeclined> execute(DeclineConferenceContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound(
                    "No conference found to decline: " + conferenceId);
        }
        return Stream.of(new ConferenceAttendanceDeclined(conferenceId, reason, declinedOn));
    }
}
