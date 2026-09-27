package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.OpenCfpCommand;
import dev.ted.jittertravel.domain.OpenCfpContext;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.OpenCfpRequest;

import java.util.Optional;
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
        OpenCfpCommand command =
                new OpenCfpCommand(conferenceId, closesOn, request.submissionUrl());
        commandExecutor.execute(commandId, request, contextFor(conferenceId), command);
    }

    /**
     * Folds to the conference's live {@link ConferencePlanned}, because the two facts this command
     * needs both come off it: that it is live, and how it forms its program.
     */
    private OpenCfpContext contextFor(ConferenceId conferenceId) {
        Optional<ConferencePlanned> planned =
                new LiveConferencePlan(conferenceId).in(commandExecutor.eventsForDecision());
        return new OpenCfpContext(planned.isPresent(),
                                  planned.map(ConferencePlanned::format).orElse(null));
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
