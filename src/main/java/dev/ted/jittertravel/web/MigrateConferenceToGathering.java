package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Command record for migrating a tentative conference to a gathering. It is the durable,
 * self-contained representation of that action, capturing everything needed to emit its events
 * (notably the generated {@code gatheringId}). {@link #events()} is the single source of those
 * events, applied on the live path via {@code CommandExecutor.appendEvents}.
 * <p>
 * The venue zone is derived from {@link #location()} in {@link #events()}, so an unresolvable
 * location fails loudly before anything is written.
 */
public record MigrateConferenceToGathering(
        UUID conferenceId,
        UUID gatheringId,
        String title,
        String venueName,
        Address location,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean speaking,
        String infoUrl,
        String cancellationReason
) {

    public Stream<? extends Event> events() {
        ZoneId zone = new LocationZoneResolver().resolve(location);
        return Stream.of(
                new ConferenceCancelled(ConferenceId.of(conferenceId), cancellationReason),
                new GatheringPlanned(GatheringId.of(gatheringId), title, venueName, location,
                        ZonedTimestamp.fromLocal(date.atTime(startTime), zone),
                        ZonedTimestamp.fromLocal(date.atTime(endTime), zone),
                        speaking, infoUrl)
        );
    }
}