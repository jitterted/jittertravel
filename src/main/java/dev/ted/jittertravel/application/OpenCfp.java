package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.OpenCfpCommand;
import dev.ted.jittertravel.domain.OpenCfpContext;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.OpenCfpRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that a conference's call for papers is open, and when it closes.
 * <p>
 * Mirrors {@link ConfirmConferenceAttendance}: the one decision fact — is this conference still
 * live? — is folded from the authoritative event stream rather than read off a projector (R1 in
 * {@code EventSourcingRulesHeuristics.md}), so the executor is all this service needs.
 * <p>
 * The deadline arrives already zoned. The venue zone is the conference's own, taken at the boundary
 * from the dates {@code ConferencePlanned} stored, so it cannot disagree with them — see
 * {@link dev.ted.jittertravel.domain.CfpOpened}. commandId is captured at the boundary too; this
 * service does no clock or UUID I/O of its own.
 */
public class OpenCfp {
    private final CommandExecutor commandExecutor;

    public OpenCfp(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void openCfp(UUID commandId, OpenCfpRequest request, ZonedTimestamp closesOn) {
        ConferenceId conferenceId = ConferenceId.of(request.conferenceId());
        OpenCfpCommand command = new OpenCfpCommand(conferenceId, closesOn);
        commandExecutor.execute(commandId, request, contextFor(conferenceId), command);
    }

    private OpenCfpContext contextFor(ConferenceId conferenceId) {
        boolean exists = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(false,
                        (current, event) -> stillPlanned(current, conferenceId, event),
                        (first, second) -> second);
        return new OpenCfpContext(exists);
    }

    private boolean stillPlanned(boolean current, ConferenceId wanted, Object event) {
        return switch (event) {
            case ConferencePlanned e when e.conferenceId().equals(wanted) -> true;
            case ConferenceCancelled e when e.conferenceId().equals(wanted) -> false;
            case ConferenceAttendanceDeclined e when e.conferenceId().equals(wanted) -> false;
            default -> current;
        };
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
