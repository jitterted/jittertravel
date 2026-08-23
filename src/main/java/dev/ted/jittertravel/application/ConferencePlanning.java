package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceHasNoCfp;
import dev.ted.jittertravel.domain.PlanConferenceCommand;
import dev.ted.jittertravel.domain.PlanConferenceContext;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.OpenCfpRequest;
import dev.ted.jittertravel.web.PlanConferenceRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Plans a conference — and, when the form carried one, records its CFP in the same submit.
 * <p>
 * <strong>Two commands, one submit</strong> ({@code docs/SessionizePrefillPlan.md}, Slice 0).
 * {@code PlanConferenceCommand} and {@code OpenCfpCommand} stay separate commands producing
 * separate events, so {@code CfpOpened} is unchanged by having a second way in and the audit trail
 * carries two command rows with two commandIds — both captured at the boundary, like every other
 * nondeterministic input.
 * <p>
 * <strong>The order is forced, and it is not atomic.</strong> {@link OpenCfp} folds the stream for a
 * live {@code ConferencePlanned} before it will emit, so the plan command has to land first. If the
 * second one then fails, the conference exists with no CFP — recoverable, because
 * {@code /conferences/{id}/cfp} is exactly that repair. What keeps that from happening in the
 * ordinary case is {@link #refuseImpossibleCfp}: everything knowable from the form alone is checked
 * <em>before</em> anything is written, so a rejected submit leaves no half-planned conference behind
 * and the form can be re-rendered with its error the way every other form is.
 * <p>
 * <strong>The zone is resolved once.</strong> {@link PlanConferenceHandler} already resolves the
 * venue zone and stamps it on the command's dates; the CFP deadline takes <em>that</em> zone rather
 * than resolving from the address a second time, which could only disagree with the first. It is the
 * same rule {@code OpenCfpController} follows for the standalone page, where the zone comes off the
 * conference's own stored dates.
 */
public class ConferencePlanning {
    private final CommandExecutor commandExecutor;
    private final LocationZoneResolver zoneResolver;
    private final OpenCfp openCfp;

    public ConferencePlanning(CommandExecutor commandExecutor,
                              LocationZoneResolver zoneResolver,
                              OpenCfp openCfp) {
        this.commandExecutor = commandExecutor;
        this.zoneResolver = zoneResolver;
        this.openCfp = openCfp;
    }

    /**
     * @param now          captured at the boundary (controller); this service reads no clock.
     * @param cfpCommandId the second commandId, captured at the boundary too and unused when the
     *                     form carried no CFP. Passed in rather than generated here for the same
     *                     reason {@code now} is: a service that mints its own UUIDs cannot be
     *                     pinned by a test.
     */
    public void planConference(PlanConferenceRequest request, Instant now, UUID cfpCommandId) {
        PlanConferenceCommand command = new PlanConferenceHandler(zoneResolver).handle(request);
        refuseImpossibleCfp(request, command.format());

        PlanConferenceContext context = new PlanConferenceContext(now);
        commandExecutor.execute(command.conferenceId().id(), request, context, command);

        if (request.getCfpClosesOn() != null) {
            // The conference's own zone, off the dates the command just resolved.
            openCfp.openCfp(cfpCommandId,
                            new OpenCfpRequest(command.conferenceId().id(),
                                               request.getCfpClosesOn(),
                                               request.getCfpSubmissionUrl()),
                            ZonedTimestamp.fromLocal(request.getCfpClosesOn(),
                                                     command.startDate().zone()));
        }
    }

    /**
     * The two CFP mistakes the form can make, both refused <strong>before the first command runs</strong>
     * so that neither can leave a conference planned with a rejected CFP beside it.
     * <p>
     * {@link ConferenceHasNoCfp} is the domain's own exception, thrown here for the same reason
     * {@code OpenCfpCommand} throws it: an open-space conference chooses its sessions on the day.
     * That refusal still stands in the command — this only moves it earlier, to where it can be
     * reported as a field error instead of arriving after a write.
     * <p>
     * A submission URL with no deadline is refused rather than dropped: {@code CfpOpened} is built
     * around its deadline and cannot carry a URL alone, and silently discarding what Ted typed is
     * the worse of the two ways to handle it.
     */
    private void refuseImpossibleCfp(PlanConferenceRequest request, ConferenceFormat format) {
        LocalDateTime closesOn = request.getCfpClosesOn();
        boolean hasSubmissionUrl = request.getCfpSubmissionUrl() != null
                                   && !request.getCfpSubmissionUrl().isBlank();
        if (format == ConferenceFormat.OPEN_SPACE && (closesOn != null || hasSubmissionUrl)) {
            throw new ConferenceHasNoCfp(
                    "An open-space conference chooses its sessions on the day — there is no call for papers");
        }
        if (closesOn == null && hasSubmissionUrl) {
            throw new CfpDeadlineMissing(
                    "Recording where the talk is submitted needs the closing date too");
        }
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
