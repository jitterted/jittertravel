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

    /**
     * Folds to the conference's own {@link ConferencePlanned} — or null once it is cancelled or
     * declined — because the two facts this command needs both come off it: that it is live, and
     * how it forms its program.
     */
    private OpenCfpContext contextFor(ConferenceId conferenceId) {
        ConferencePlanned planned = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce((ConferencePlanned) null,
                        (current, event) -> stillPlanned(current, conferenceId, event),
                        (first, second) -> second);
        return new OpenCfpContext(planned != null,
                                  planned == null ? null : planned.format());
    }

    private ConferencePlanned stillPlanned(ConferencePlanned current, ConferenceId wanted, Object event) {
        return switch (event) {
            case ConferencePlanned e when e.conferenceId().equals(wanted) -> e;
            case ConferenceCancelled e when e.conferenceId().equals(wanted) -> null;
            case ConferenceAttendanceDeclined e when e.conferenceId().equals(wanted) -> null;
            default -> current;
        };
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
