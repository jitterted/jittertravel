package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class ConferenceMigrationService {

    private final TentativeConferenceProjector tentativeConferenceProjector;
    private final CommandExecutor commandExecutor;

    public ConferenceMigrationService(TentativeConferenceProjector tentativeConferenceProjector,
                                      CommandExecutor commandExecutor) {
        this.tentativeConferenceProjector = tentativeConferenceProjector;
        this.commandExecutor = commandExecutor;
    }

    public void migrateToGathering(ConferenceId conferenceId, boolean speaking) {
        TentativeConferenceView conference = tentativeConferenceProjector.findById(conferenceId)
                .orElseThrow(() -> new IllegalArgumentException("Conference not found: " + conferenceId));

        if (!conference.startDate().toLocalDate().equals(conference.endDate().toLocalDate())) {
            throw new ConferenceSpansMultipleDays(
                    "Cannot migrate \"" + conference.name() + "\": start and end dates differ");
        }

        commandExecutor.appendEvents(
                UUID.randomUUID(),
                Map.of("type", "migrateConferenceToGathering", "conferenceId", conferenceId.id()),
                Stream.of(
                        new ConferenceCancelled(conferenceId, "Migrated to gathering"),
                        new GatheringPlanned(
                                GatheringId.random(),
                                conference.name(),
                                conference.venueName(),
                                conference.venueAddress(),
                                conference.startDate().toLocalDate(),
                                conference.startDate().toLocalTime(),
                                conference.endDate().toLocalTime(),
                                speaking,
                                ""
                        )
                )
        );
    }
}
