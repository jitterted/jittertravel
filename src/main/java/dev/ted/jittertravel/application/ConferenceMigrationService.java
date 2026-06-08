package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceSpansMultipleDays;

import java.util.UUID;

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

        MigrateConferenceToGathering command = new MigrateConferenceToGathering(
                conferenceId.id(),
                UUID.randomUUID(),
                conference.name(),
                conference.venueName(),
                conference.venueAddress(),
                conference.startDate().toLocalDate(),
                conference.startDate().toLocalTime(),
                conference.endDate().toLocalTime(),
                speaking,
                "",
                "Migrated to gathering"
        );

        commandExecutor.appendEvents(UUID.randomUUID(), command, command.events());
    }
}
