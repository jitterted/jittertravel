package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Records that Ted is going to a conference he had been watching, emitting
 * {@link ConferenceAttendanceConfirmed}.
 * <p>
 * The only refusal is a conference that does not exist (never planned, or already cancelled by the
 * organizers, or already declined). Like {@link DeclineConferenceCommand} there is deliberately no
 * time gate: deciding to go can happen at any point, and telling JitterTravel about it is a manual
 * step that may lag the real decision — which is exactly what the backfill pass over the existing
 * conferences is.
 * <p>
 * <strong>Confirming twice is allowed on purpose.</strong> A second confirmation with a different
 * {@link AttendanceBasis} is how "I'd bought a ticket, and now the talk was accepted" gets recorded,
 * and re-recording the same basis is harmless — the fold takes the last one. There is nothing to
 * protect against here, so the command does not consult prior confirmations at all; contrast
 * {@link DeclineConferenceCommand}, where a second decline would be a duplicate of a decision that
 * already removed the conference from every read model.
 */
public record ConfirmConferenceAttendanceCommand(
        ConferenceId conferenceId,
        AttendanceBasis basis,
        Instant confirmedOn
) implements DomainCommand<ConfirmConferenceAttendanceContext> {

    @Override
    public Stream<ConferenceAttendanceConfirmed> execute(ConfirmConferenceAttendanceContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound(
                    "No conference found to confirm attendance for: " + conferenceId);
        }
        return Stream.of(new ConferenceAttendanceConfirmed(conferenceId, basis, confirmedOn));
    }
}
