package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.PlanConferenceCommand;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.PlanConferenceRequest;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Maps the conference form to its domain command, resolving the venue zone once: an explicit
 * {@code CommonZone} pick wins, otherwise the venue address must resolve or the command is rejected
 * with a {@link ZoneResolutionException} and the form re-prompts for a pick. A conference has one
 * {@code venueAddress}, so start and end share that single zone.
 */
public class PlanConferenceHandler {

    private final VenueZone venueZone;

    public PlanConferenceHandler(LocationZoneResolver zoneResolver) {
        this.venueZone = new VenueZone(zoneResolver);
    }

    public PlanConferenceCommand handle(PlanConferenceRequest request) {
        Address venueAddress = request.venueAddress();
        ZoneId zone = venueZone.resolve(request.zone(), venueAddress);
        return new PlanConferenceCommand(
                ConferenceId.of(UUID.fromString(request.conferenceId())),
                request.name(),
                ZonedTimestamp.fromLocal(request.startDate(), zone),
                ZonedTimestamp.fromLocal(request.endDate(), zone),
                request.venueName(),
                venueAddress,
                ConferenceFormat.fromParam(request.format()),
                request.infoUrl()
        );
    }
}
