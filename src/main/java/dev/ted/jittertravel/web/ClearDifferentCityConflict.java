package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.DifferentCityConflictCleared;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringId;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * Command record for clearing a different-city conflict between a gathering and a conference.
 * Carries everything needed to re-emit its event on import (see {@link #events()}), so it
 * round-trips through export/import.
 */
public record ClearDifferentCityConflict(
        UUID gatheringId,
        UUID conferenceId,
        String reason
) implements ImportableCommand {

    @Override
    public UUID commandId() {
        return UUID.randomUUID();
    }

    @Override
    public Stream<? extends Event> events() {
        return Stream.of(new DifferentCityConflictCleared(
                GatheringId.of(gatheringId), ConferenceId.of(conferenceId), reason));
    }
}