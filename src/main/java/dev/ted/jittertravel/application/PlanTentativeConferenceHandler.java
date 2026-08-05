package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.PlanTentativeConferenceCommand;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Maps the conference form to its domain command, resolving the venue zone once: an explicit
 * {@code CommonZone} pick wins, otherwise the venue address must resolve or the command is rejected
 * with a {@link ZoneResolutionException} and the form re-prompts for a pick. A conference has one
 * {@code venueAddress}, so start and end share that single zone.
 */
public class PlanTentativeConferenceHandler {

    private final VenueZone venueZone;

    public PlanTentativeConferenceHandler(LocationZoneResolver zoneResolver) {
        this.venueZone = new VenueZone(zoneResolver);
    }

    public PlanTentativeConferenceCommand handle(PlanTentativeConferenceRequest request) {
        Address venueAddress = request.getVenueAddress();
        ZoneId zone = venueZone.resolve(request.getZone(), venueAddress);
        return new PlanTentativeConferenceCommand(
                ConferenceId.of(UUID.fromString(request.getConferenceId())),
                request.getName(),
                zonedOrNull(request.getStartDate(), zone),
                zonedOrNull(request.getEndDate(), zone),
                request.getVenueName(),
                venueAddress
        );
    }

    /**
     * A missing date stays null for the command to reject ({@code DateRangeNotInFuture} /
     * {@code InvalidDateRange}), which the controller maps to a field error — rather than the
     * handler throwing a raw NPE the form has no way to report.
     */
    private ZonedTimestamp zonedOrNull(LocalDateTime wallClock, ZoneId zone) {
        return wallClock == null ? null : ZonedTimestamp.fromLocal(wallClock, zone);
    }
}
