package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeConferenceDatesCommand;
import dev.ted.jittertravel.domain.ChangeConferenceDatesContext;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.ChangeConferenceDatesRequest;

import java.util.UUID;

/**
 * Records that a conference's organizers moved it.
 * <p>
 * Mirrors {@link OpenCfp}: both decision facts — is this conference still live, and which zone was
 * it planned in — come off the conference's own {@link ConferencePlanned}, folded from the
 * authoritative event stream rather than read off a projector (R1 in
 * {@code EventSourcingRulesHeuristics.md}). commandId is captured at the boundary; this service
 * does no clock or UUID I/O of its own, and there is no {@code now} because the change is not
 * time-gated.
 */
public class ChangeConferenceDates {
    private final CommandExecutor commandExecutor;

    public ChangeConferenceDates(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * {@code conferenceIdValue} arrives from the path rather than on the request: which conference
     * moved is not something the form submits, so there is nothing on the page for a crafted POST
     * to re-target.
     */
    public void changeDates(UUID commandId, UUID conferenceIdValue, ChangeConferenceDatesRequest request) {
        ConferenceId conferenceId = ConferenceId.of(conferenceIdValue);
        commandExecutor.execute(commandId, request, contextFor(conferenceId),
                new ChangeConferenceDatesCommand(conferenceId, request.startDate(), request.endDate()));
    }

    /**
     * An earlier {@code ConferenceDatesChanged} is deliberately not folded: it moves the dates,
     * never the zone and never whether the conference exists, so it cannot change either answer.
     */
    private ChangeConferenceDatesContext contextFor(ConferenceId conferenceId) {
        ConferencePlanned planned = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce((ConferencePlanned) null,
                        (current, event) -> stillPlanned(current, conferenceId, event),
                        (first, second) -> second);
        return new ChangeConferenceDatesContext(planned != null,
                                                planned == null ? null : planned.startDate().zone());
    }

    private ConferencePlanned stillPlanned(ConferencePlanned current, ConferenceId wanted, Object event) {
        return switch (event) {
            case ConferencePlanned e when e.conferenceId().equals(wanted) -> e;
            case ConferenceCancelled e when e.conferenceId().equals(wanted) -> null;
            case ConferenceAttendanceDeclined e when e.conferenceId().equals(wanted) -> null;
            default -> current;
        };
    }
}
