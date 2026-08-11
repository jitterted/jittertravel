package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.DifferentCityConflictCleared;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringId;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * Internal-action command for clearing a different-city conflict between a gathering and a
 * conference. {@link #events()} is the single source of its event, applied on the live path via
 * {@code CommandExecutor.appendEvents}.
 */
public record ClearDifferentCityConflict(
        UUID gatheringId,
        UUID conferenceId,
        String reason
) {

    public Stream<? extends Event> events() {
        return Stream.of(new DifferentCityConflictCleared(
                GatheringId.of(gatheringId), ConferenceId.of(conferenceId), reason));
    }
}