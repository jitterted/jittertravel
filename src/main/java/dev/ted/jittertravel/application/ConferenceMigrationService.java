package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceSpansMultipleDays;
import dev.ted.jittertravel.web.MigrateConferenceToGathering;

import java.time.LocalDateTime;
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

        // Single-day is judged in the venue's own zone, matching the migratable list.
        LocalDateTime start = conference.startDate().localDateTime();
        LocalDateTime end = conference.endDate().localDateTime();
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new ConferenceSpansMultipleDays(
                    "Cannot migrate \"" + conference.name() + "\": start and end dates differ");
        }

        MigrateConferenceToGathering command = new MigrateConferenceToGathering(
                conferenceId.id(),
                UUID.randomUUID(),
                conference.name(),
                conference.venueName(),
                conference.venueAddress(),
                start.toLocalDate(),
                start.toLocalTime(),
                end.toLocalTime(),
                speaking,
                "",
                "Migrated to gathering"
        );

        commandExecutor.appendEvents(UUID.randomUUID(), command, command.events());
    }
}
