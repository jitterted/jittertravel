package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Command record for migrating a tentative conference to a gathering. It is the durable,
 * exportable representation of that action: it captures everything needed to deterministically
 * re-emit its events on import (notably the generated {@code gatheringId}), so it round-trips
 * through export/import. Use {@link #events()} as the single source of the emitted events for
 * both the live action and import replay.
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
        return Stream.of(
                new ConferenceCancelled(ConferenceId.of(conferenceId), cancellationReason),
                new GatheringPlanned(GatheringId.of(gatheringId), title, venueName, location,
                        date, startTime, endTime, speaking, infoUrl)
        );
    }
}
