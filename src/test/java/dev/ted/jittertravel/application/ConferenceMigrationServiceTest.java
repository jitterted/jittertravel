package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConferenceMigrationServiceTest {

    private static final ZoneId VENUE_ZONE = ZoneId.of("America/Los_Angeles");
    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 20, 18, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 6, 20, 21, 0);

    @Test
    void migrateWithSpeakingTrueEmitsGatheringPlannedWithSpeakingTrue() {
        CapturingCommandExecutor commandExecutor = new CapturingCommandExecutor();
        ConferenceId conferenceId = ConferenceId.random();
        ConferenceMigrationService service = serviceFor(conferenceId, commandExecutor);

        service.migrateToGathering(conferenceId, true);

        assertThat(commandExecutor.gatheringPlanned().speaking())
                .as("speaking flag from migration")
                .isTrue();
    }

    @Test
    void migrateWithSpeakingFalseEmitsGatheringPlannedWithSpeakingFalse() {
        CapturingCommandExecutor commandExecutor = new CapturingCommandExecutor();
        ConferenceId conferenceId = ConferenceId.random();
        ConferenceMigrationService service = serviceFor(conferenceId, commandExecutor);

        service.migrateToGathering(conferenceId, false);

        assertThat(commandExecutor.gatheringPlanned().speaking())
                .as("speaking flag from migration")
                .isFalse();
    }

    private static ConferenceMigrationService serviceFor(ConferenceId conferenceId,
                                                         CommandExecutor commandExecutor) {
        ConferenceProjector projector = new ConferenceProjector();
        projector.handle(Stream.of(stored(new ConferencePlanned(
                conferenceId,
                "JitterConf 2026",
                zt(START),
                zt(END),
                "Moscone Center",
                new Address("747 Howard St", "San Francisco", "CA", "94103", "US", null)
        ))));
        return new ConferenceMigrationService(projector, commandExecutor);
    }

    /** The venue is in San Francisco, so its wall-clock is Pacific — not the UTC-pinned test JVM. */
    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, VENUE_ZONE);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }

    private static final class CapturingCommandExecutor extends CommandExecutor {
        private List<? extends Event> capturedEvents = List.of();

        private CapturingCommandExecutor() {
            super(null, null);
        }

        @Override
        public void appendEvents(UUID commandId, Object commandRecord, Stream<? extends Event> events) {
            capturedEvents = events.toList();
        }

        GatheringPlanned gatheringPlanned() {
            return capturedEvents.stream()
                    .filter(GatheringPlanned.class::isInstance)
                    .map(GatheringPlanned.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No GatheringPlanned event was emitted"));
        }
    }
}
