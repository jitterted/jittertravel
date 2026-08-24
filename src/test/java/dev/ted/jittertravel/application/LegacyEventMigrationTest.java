package dev.ted.jittertravel.application;

import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupCommandRow;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupEventRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The eager migration bakes the current payload shape and the per-type schema-version stamp into
 * {@code event_log}, once (see {@code docs/archived/LegacyEventEagerMigrationPlan.md}). Legacy rows are seeded
 * verbatim through the restore path — the only way to write a bare-scalar payload with a null stamp,
 * since the append path always stamps and stores the current shape.
 */
@SpringBootTest
class LegacyEventMigrationTest extends AbstractTestcontainerIntegrationTest {

    @Autowired LegacyEventMigration migration;
    @Autowired PostgresPersister persister;

    // A legacy HotelBooked: bare-scalar checkIn/checkOut, no stamp. 2026-06-17 is BST (+01:00) in
    // London, so 15:00 local == 14:00Z after upcast.
    private static final String LEGACY_HOTEL = """
            {
              "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
              "hotelName": "Milton Mill House",
              "address": {
                "street": "Milton Hill", "city": "Steventon", "region": "Oxfordshire",
                "postalCode": "OX13 6AF", "country": "UK", "locationForMatching": "Steventon"
              },
              "checkIn": "2026-06-17T15:00:00",
              "checkOut": "2026-06-21T11:00:00",
              "bookingIntent": "FINAL"
            }
            """;

    // Same shape but an unresolvable location, so upcast throws ZoneResolutionException.
    private static final String LEGACY_HOTEL_UNRESOLVABLE = """
            {
              "hotelBookingId": {"id": "44444444-4444-4444-4444-444444444444"},
              "hotelName": "Nowhere Inn",
              "address": {
                "street": "1 Nowhere", "city": "Zzxqville", "region": "",
                "postalCode": "00000", "country": "Freedonia", "locationForMatching": "Zzxqville"
              },
              "checkIn": "2026-06-17T15:00:00",
              "checkOut": "2026-06-21T11:00:00",
              "bookingIntent": "FINAL"
            }
            """;

    // A ConferenceAttendanceDeclined — a version-1 type born after the ZonedTimestamp change, so its
    // payload is already current-shape; only the missing stamp needs adding.
    private static final String DECLINED_CURRENT_SHAPE = """
            {
              "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
              "reason": "Schedule clash",
              "declinedOn": "2026-08-16T18:30:00Z"
            }
            """;

    // A ConferencePlanned already in its v3 shape (zoned timestamps, "region", a format). Seeded under
    // the retired wire id "ConferenceTentativelyPlanned" so the stored NAME is its only staleness.
    private static final String CONFERENCE_CURRENT_SHAPE = """
            {
              "conferenceId": {"id": "55555555-5555-5555-5555-555555555555"},
              "name": "JitterConf",
              "startDate": {"utc": "2026-09-15T16:00:00Z", "zone": "America/Los_Angeles"},
              "endDate": {"utc": "2026-09-18T00:00:00Z", "zone": "America/Los_Angeles"},
              "venueName": "Moscone Center",
              "venueAddress": {
                "street": "747 Howard St", "city": "San Francisco", "region": "CA",
                "postalCode": "94103", "country": "USA", "locationForMatching": "San Francisco"
              },
              "format": "OPEN_SPACE"
            }
            """;

    @Test
    void migratesLegacyHotelPayloadToZonedShapeStampsVersion2AndKeepsIdentity() {
        UUID commandId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OffsetDateTime ts = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        seedLegacyEvent(commandId, eventId, 1L, ts, "HotelBooked", LEGACY_HOTEL);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.hasErrors())
                .as("errors: %s", result.errors())
                .isFalse();
        assertThat(result.rewritten()).isEqualTo(1);
        assertThat(result.stamped()).isZero();

        assertThat(persister.findAllEventsForBackup())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.schemaVersion())
                            .as("row is stamped with the type's current schema version")
                            .isEqualTo(2);
                    assertThat(e.payloadJson())
                            .as("checkIn is now a {utc,zone} object")
                            .contains("utc")
                            .contains("Europe/London")
                            .contains("2026-06-17T14:00:00Z")          // 15:00 BST -> 14:00Z
                            .doesNotContain("2026-06-17T15:00:00");    // the bare local scalar is gone
                    // Verbatim identity: only payload + stamp changed.
                    assertThat(e.sequence()).isEqualTo(1L);
                    assertThat(e.eventId()).isEqualTo(eventId);
                    assertThat(e.commandId()).isEqualTo(commandId);
                    assertThat(e.timestamp().toInstant()).isEqualTo(ts.toInstant());
                });
    }

    @Test
    void isIdempotentSecondRunRewritesNothing() {
        seedLegacyEvent(UUID.randomUUID(), UUID.randomUUID(), 1L,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"), "HotelBooked", LEGACY_HOTEL);
        migration.migrate();

        LegacyEventMigration.MigrationResult second = migration.migrate();

        assertThat(second.hasErrors()).isFalse();
        assertThat(second.rewritten()).isZero();
        assertThat(second.stamped()).isZero();
        assertThat(migration.preview().totalToMigrate())
                .as("a fully-migrated store has nothing left to migrate")
                .isZero();
    }

    @Test
    void stampsAVersion1TypeWithoutRewritingItsPayload() {
        UUID commandId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        seedLegacyEvent(commandId, eventId, 1L, OffsetDateTime.parse("2026-08-16T18:30:00Z"),
                "ConferenceAttendanceDeclined", DECLINED_CURRENT_SHAPE);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.rewritten())
                .as("an already-current payload is not rewritten")
                .isZero();
        assertThat(result.stamped()).isEqualTo(1);

        assertThat(persister.findAllEventsForBackup())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.schemaVersion())
                            .as("a type born after the ZonedTimestamp change stays at version 1")
                            .isEqualTo(1);
                    assertThat(e.payloadJson())
                            .as("payload content is unchanged")
                            .contains("Schedule clash");
                });
    }

    @Test
    void renamesARetiredWireIdToTheCurrentLogicalNameLeavingPayloadAndStampAlone() {
        UUID commandId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OffsetDateTime ts = OffsetDateTime.parse("2026-08-01T10:00:00Z");
        // Current shape, correctly stamped: the retired wire id is this row's ONLY staleness.
        seedStampedEvent(commandId, eventId, 1L, ts,
                "ConferenceTentativelyPlanned", CONFERENCE_CURRENT_SHAPE, 3);

        assertThat(migration.preview().toRename())
                .as("a rename-only row is selected, not skipped as already-current")
                .isEqualTo(1);
        assertThat(migration.preview().totalToMigrate())
                .as("rows to write counts the rename-only row")
                .isEqualTo(1);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.hasErrors())
                .as("errors: %s", result.errors())
                .isFalse();
        assertThat(result.renamed()).isEqualTo(1);
        assertThat(result.rewritten()).isZero();
        assertThat(result.stamped()).isZero();

        assertThat(persister.findAllEventsForBackup())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.type())
                            .as("the retired wire id is normalized to the current logical name")
                            .isEqualTo("ConferencePlanned");
                    assertThat(e.schemaVersion())
                            .as("the stamp is left where it was")
                            .isEqualTo(3);
                    assertThat(e.payloadJson())
                            .as("the payload is untouched")
                            .contains("OPEN_SPACE")
                            .contains("2026-09-15T16:00:00Z");
                    // Verbatim identity: only type changed.
                    assertThat(e.sequence()).isEqualTo(1L);
                    assertThat(e.eventId()).isEqualTo(eventId);
                    assertThat(e.commandId()).isEqualTo(commandId);
                    assertThat(e.timestamp().toInstant()).isEqualTo(ts.toInstant());
                });

        LegacyEventMigration.MigrationResult second = migration.migrate();
        assertThat(second.renamed())
                .as("a normalized row is not renamed twice")
                .isZero();
        assertThat(migration.preview().totalToMigrate()).isZero();
    }

    @Test
    void normalizesALegacyFqcnTypeInTheSameWriteAsThePayloadAndStamp() {
        seedLegacyEvent(UUID.randomUUID(), UUID.randomUUID(), 1L,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"),
                "dev.ted.jittertravel.domain.HotelBooked", LEGACY_HOTEL);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.hasErrors())
                .as("errors: %s", result.errors())
                .isFalse();
        assertThat(result.rewritten())
                .as("one row, one write — the counters overlap rather than counting it twice")
                .isEqualTo(1);
        assertThat(result.renamed()).isEqualTo(1);

        assertThat(persister.findAllEventsForBackup())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.type()).isEqualTo("HotelBooked");
                    assertThat(e.schemaVersion()).isEqualTo(2);
                    assertThat(e.payloadJson()).contains("2026-06-17T14:00:00Z");
                });
    }

    @Test
    void oneUnbindableRowAbortsTheWholeMigrationLeavingEveryRowUntouched() {
        seedLegacyEvent(UUID.randomUUID(), UUID.randomUUID(), 1L,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"), "HotelBooked", LEGACY_HOTEL);
        seedLegacyEvent(UUID.randomUUID(), UUID.randomUUID(), 2L,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"), "HotelBooked", LEGACY_HOTEL_UNRESOLVABLE);
        seedStampedEvent(UUID.randomUUID(), UUID.randomUUID(), 3L,
                OffsetDateTime.parse("2026-08-01T10:00:00Z"),
                "ConferenceTentativelyPlanned", CONFERENCE_CURRENT_SHAPE, 3);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.hasErrors())
                .as("the unresolvable row is reported")
                .isTrue();
        assertThat(result.rewritten()).isZero();
        assertThat(result.errors())
                .anySatisfy(err -> assertThat(err).contains("Event 2"));

        assertThat(persister.findAllEventsForBackup())
                .filteredOn(e -> e.sequence() == 1L)
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.schemaVersion())
                            .as("the good row is left unstamped — nothing was written")
                            .isNull();
                    assertThat(e.payloadJson())
                            .as("the good row still holds its bare-scalar payload")
                            .contains("2026-06-17T15:00:00");
                });
        assertThat(persister.findAllEventsForBackup())
                .filteredOn(e -> e.sequence() == 3L)
                .singleElement()
                .satisfies(e -> assertThat(e.type())
                        .as("the rename-only row keeps its retired wire id — nothing was written")
                        .isEqualTo("ConferenceTentativelyPlanned"));
    }

    private void seedLegacyEvent(UUID commandId, UUID eventId, long sequence,
                                 OffsetDateTime timestamp, String type, String payloadJson) {
        // null schemaVersion == a pre-stamp legacy row, exactly what the migration heals.
        seedStampedEvent(commandId, eventId, sequence, timestamp, type, payloadJson, null);
    }

    private void seedStampedEvent(UUID commandId, UUID eventId, long sequence, OffsetDateTime timestamp,
                                  String type, String payloadJson, Integer schemaVersion) {
        persister.restoreCommandsAndEvents(
                List.of(new BackupCommandRow(commandId, timestamp, "SeedCommand", "{}",
                        null, "SUCCEEDED", null)),
                List.of(new BackupEventRow(sequence, eventId, commandId, timestamp, type, payloadJson,
                        schemaVersion)));
    }
}
