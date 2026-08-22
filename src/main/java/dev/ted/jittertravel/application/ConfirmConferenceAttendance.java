package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.ConfirmConferenceAttendanceCommand;
import dev.ted.jittertravel.domain.ConfirmConferenceAttendanceContext;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.ConfirmConferenceAttendanceRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that Ted is going to a conference he had been watching.
 * <p>
 * Mirrors {@link DeclineConference}: it folds its one decision fact from the authoritative event
 * stream rather than a projector (R1 in {@code EventSourcingRulesHeuristics.md}), so the executor is
 * all it needs. A conference is "live" while it has been planned and neither declined nor cancelled
 * by the organizers — both clear the fact, so confirming a conference that is already gone is
 * refused as not-found. A prior confirmation does <em>not</em> clear it: re-confirming with a
 * different basis is a legitimate correction, and the fold takes the last one.
 * <p>
 * commandId and confirmedOn (the nondeterministic inputs) are captured at the boundary and passed
 * in; this service does no clock or UUID I/O of its own.
 */
public class ConfirmConferenceAttendance {
    private final CommandExecutor commandExecutor;

    public ConfirmConferenceAttendance(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void confirmAttendance(UUID commandId, ConfirmConferenceAttendanceRequest request,
                                  Instant confirmedOn) {
        ConferenceId conferenceId = ConferenceId.of(request.conferenceId());
        ConfirmConferenceAttendanceCommand command =
                new ConfirmConferenceAttendanceCommand(conferenceId, request.basis(), confirmedOn);
        commandExecutor.execute(commandId, request, contextFor(conferenceId), command);
    }

    private ConfirmConferenceAttendanceContext contextFor(ConferenceId conferenceId) {
        boolean exists = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(false,
                        (current, event) -> stillPlanned(current, conferenceId, event),
                        (first, second) -> second);
        return new ConfirmConferenceAttendanceContext(exists);
    }

    private boolean stillPlanned(boolean current, ConferenceId wanted, Object event) {
        return switch (event) {
            case ConferencePlanned e when e.conferenceId().equals(wanted) -> true;
            case ConferenceCancelled e when e.conferenceId().equals(wanted) -> false;
            case ConferenceAttendanceDeclined e when e.conferenceId().equals(wanted) -> false;
            default -> current;
        };
    }
}
