package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.DeclineConferenceCommand;
import dev.ted.jittertravel.domain.DeclineConferenceContext;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.DeclineConferenceRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Records Ted's decision not to attend a planned conference.
 * <p>
 * Like {@link CancelHotel}, it folds its one decision fact from the authoritative event stream
 * rather than a projector (R1 in {@code EventSourcingRulesHeuristics.md}), so the executor is all it
 * needs. A conference is "live" while it has been planned and neither declined nor cancelled by the
 * organizers — both clear the fact, so declining a conference that is already gone is refused as
 * not-found rather than emitting a duplicate event.
 * <p>
 * commandId and declinedOn (the nondeterministic inputs) are captured at the boundary and passed in;
 * this service does no clock or UUID I/O of its own.
 */
public class DeclineConference {
    private final CommandExecutor commandExecutor;

    public DeclineConference(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void declineConference(UUID commandId, DeclineConferenceRequest request, Instant declinedOn) {
        ConferenceId conferenceId = ConferenceId.of(request.conferenceId());
        DeclineConferenceCommand command =
                new DeclineConferenceCommand(conferenceId, request.reason(), declinedOn);
        commandExecutor.execute(commandId, request, contextFor(conferenceId), command);
    }

    private DeclineConferenceContext contextFor(ConferenceId conferenceId) {
        boolean exists = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(false,
                        (current, event) -> stillPlanned(current, conferenceId, event),
                        (first, second) -> second);
        return new DeclineConferenceContext(exists);
    }

    private boolean stillPlanned(boolean current, ConferenceId wanted, Object event) {
        return switch (event) {
            case ConferenceTentativelyPlanned e when e.conferenceId().equals(wanted) -> true;
            case ConferenceCancelled e when e.conferenceId().equals(wanted) -> false;
            case ConferenceAttendanceDeclined e when e.conferenceId().equals(wanted) -> false;
            default -> current;
        };
    }
}
