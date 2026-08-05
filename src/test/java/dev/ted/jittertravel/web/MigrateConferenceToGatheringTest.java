package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringPlanned;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The migrate command is replayed from backups, so {@link MigrateConferenceToGathering#events()}
 * has to re-derive the venue zone from the payload's own location — the record deliberately keeps
 * wall-clock {@code date}/{@code startTime}/{@code endTime} fields so pre-migration backups replay
 * unchanged, which only works if the zone is recoverable. An unresolvable location must throw,
 * since {@code events()} runs during import validation where failing loudly costs nothing.
 */
class MigrateConferenceToGatheringTest {

    @Test
    void venueZoneIsDerivedFromTheConferenceLocation() {
        List<? extends Event> events = migrationTo("Tokyo", "Japan").events().toList();

        assertThat(events)
                .hasExactlyElementsOfTypes(ConferenceCancelled.class, GatheringPlanned.class);
        GatheringPlanned planned = (GatheringPlanned) events.get(1);
        assertThat(planned.startsAt().zone())
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(planned.startsAt().utc())
                .as("18:00 JST is 09:00Z")
                .isEqualTo(Instant.parse("2026-09-15T09:00:00Z"));
        assertThat(planned.endsAt().utc())
                .isEqualTo(Instant.parse("2026-09-15T12:00:00Z"));
    }

    @Test
    void unresolvableLocationFailsLoudlyRatherThanAssumingAZone() {
        assertThatThrownBy(() -> migrationTo("Springfield", "Freedonia").events().toList())
                .isInstanceOf(ZoneResolutionException.class);
    }

    private static MigrateConferenceToGathering migrationTo(String city, String country) {
        return new MigrateConferenceToGathering(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "JitterConf",
                "Some Venue",
                new Address("1 Example St", city, "", "", country, city),
                LocalDate.of(2026, 9, 15),
                LocalTime.of(18, 0),
                LocalTime.of(21, 0),
                true,
                "",
                "Migrated to gathering");
    }
}
