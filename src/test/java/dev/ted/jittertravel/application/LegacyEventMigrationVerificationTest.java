package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.infrastructure.EventJsonMapperFactory;
import dev.ted.jittertravel.infrastructure.EventPayloadUpcaster;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupEventRow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * The after-write check reads the database again and must say so when what is there is not what the
 * run set out to write. A real database always holds what was written, so these cases stub the
 * persister: it reports the rows as written, then answers the re-read with what a failed write would
 * have left behind.
 */
class LegacyEventMigrationVerificationTest {

    private static final OffsetDateTime WHEN = OffsetDateTime.parse("2026-09-10T10:00:00Z");

    // Devnexus 2027 as production stored it (event 98): stamped v3, no state, so its correction applies.
    private static final BackupEventRow DEVNEXUS = new BackupEventRow(
            98L, UUID.fromString("3f43b7c1-1201-4411-8ed9-a69bbba8fc2f"), UUID.randomUUID(), WHEN,
            "ConferencePlanned", """
            {
              "conferenceId": {"id": "77777777-7777-7777-7777-777777777777"},
              "name": "Devnexus 2027",
              "startDate": {"utc": "2027-03-03T13:00:00Z", "zone": "America/New_York"},
              "endDate": {"utc": "2027-03-05T22:00:00Z", "zone": "America/New_York"},
              "venueName": "Georgia World Congress Center",
              "venueAddress": {"street": "", "city": "Atlanta", "region": "", "postalCode": "",
                               "country": "United States", "locationForMatching": "Atlanta"},
              "format": "CALL_FOR_PAPERS"
            }
            """, 3);

    // A transfer from Denver airport whose airport end has no country (event 132's shape).
    private static final BackupEventRow DEN_TRANSFER = new BackupEventRow(
            132L, UUID.randomUUID(), UUID.randomUUID(), WHEN, "GroundTransferPlanned", """
            {
              "groundTransferId": {"id": "88888888-8888-8888-8888-888888888888"},
              "originAirportCode": "DEN", "originName": "",
              "origin": {"street": "", "city": "Denver", "region": "", "postalCode": "",
                         "country": "", "locationForMatching": "Denver"},
              "destinationAirportCode": "", "destinationName": "Marriott Lone Tree",
              "destination": {"street": "10345 Park Meadows Dr", "city": "Lone Tree", "region": "CO",
                              "postalCode": "80124", "country": "US", "locationForMatching": "Lone Tree"},
              "departsAt": {"utc": "2026-09-14T18:00:00Z", "zone": "America/Denver"},
              "arrivesAt": {"utc": "2026-09-14T18:45:00Z", "zone": "America/Denver"}
            }
            """, 1);

    // Devnexus again, but with a country no rung can name: what a write that corrupted the row leaves.
    private static final BackupEventRow DEVNEXUS_CORRUPTED = new BackupEventRow(
            98L, DEVNEXUS.eventId(), DEVNEXUS.commandId(), WHEN, "ConferencePlanned",
            DEVNEXUS.payloadJson().replace("United States", "Freedonia"), 3);

    private final JsonMapper jsonMapper = EventJsonMapperFactory.create();
    private final PostgresPersister persister = mock(PostgresPersister.class);
    private final LegacyEventMigration migration = new LegacyEventMigration(
            persister,
            EventPayloadUpcaster.standard(new LocationZoneResolver(), new AirportZoneResolver(), jsonMapper),
            jsonMapper,
            mock(CommandExecutor.class),
            new LocationDataCorrections(new StaticAirportCityResolver()));

    @Test
    void aWriteThatDidNotLandIsReportedAsAMismatchOnEveryClaim() {
        given(persister.findAllEventsForBackup())
                .willReturn(List.of(DEVNEXUS, DEN_TRANSFER), List.of(DEVNEXUS, DEN_TRANSFER));
        given(persister.migrateEventPayloads(anyList()))
                .willReturn(2);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.verification())
                .as("the persister said it wrote both rows, and the re-read found neither changed")
                .containsExactly(
                        new LegacyEventMigration.Check("Rows written", 2, 2),
                        new LegacyEventMigration.Check("Rows still needing migration", 0, 2),
                        new LegacyEventMigration.Check("Corrections now in the database", 1, 0),
                        new LegacyEventMigration.Check("Airport ends now filled in", 1, 0));
        assertThat(result.verified())
                .isFalse();
    }

    @Test
    void aRowTheWriteLeftUnreadableCountsAsStillNeedingMigration() {
        given(persister.findAllEventsForBackup())
                .willReturn(List.of(DEVNEXUS), List.of(DEVNEXUS_CORRUPTED));
        given(persister.migrateEventPayloads(anyList()))
                .willReturn(1);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.verification())
                .contains(new LegacyEventMigration.Check("Rows still needing migration", 0, 1));
        assertThat(result.verified())
                .isFalse();
    }

    @Test
    void anAirportEndTheTableCannotFillMeansNotEverythingIsAccountedFor() {
        BackupEventRow unknownAirport = new BackupEventRow(
                133L, UUID.randomUUID(), UUID.randomUUID(), WHEN, "GroundTransferPlanned",
                DEN_TRANSFER.payloadJson().replace("\"DEN\"", "\"ZZZ\""), 1);
        given(persister.findAllEventsForBackup())
                .willReturn(List.of(DEN_TRANSFER, unknownAirport));

        LegacyEventMigration.CorrectionsCheck corrections = migration.preview().corrections();

        assertThat(corrections.airportEnds())
                .hasSize(2);
        assertThat(corrections.airportEndsToFill())
                .as("only DEN is in the airport table")
                .isEqualTo(1);
    }

    @Test
    void everyCorrectionMadeButOneAirportEndUnfillableIsNotAllAccountedFor() {
        LocationDataCorrections.Approved devnexus = new LocationDataCorrections.Approved(
                DEVNEXUS.eventId(), "Devnexus 2027",
                List.of(new LocationDataCorrections.FieldChange("region", "", "GA")));
        LegacyEventMigration.CorrectionsCheck corrections = new LegacyEventMigration.CorrectionsCheck(
                List.of(new LegacyEventMigration.CorrectionLine(98L, devnexus,
                                                                LegacyEventMigration.CorrectionState.WILL_BE_MADE)),
                List.of(new LegacyEventMigration.AirportEndLine(
                        133L, new LocationDataCorrections.AirportEndFill("ZZZ", "", "", false))));

        assertThat(corrections.accountedFor())
                .isEqualTo(corrections.expected());
        assertThat(corrections.allAccountedFor())
                .as("the one airport end that cannot be filled in keeps the verdict from being all")
                .isFalse();
    }

    @Test
    void fewerRowsWrittenThanPlannedIsAMismatch() {
        given(persister.findAllEventsForBackup())
                .willReturn(List.of(DEVNEXUS, DEN_TRANSFER), List.of());
        given(persister.migrateEventPayloads(anyList()))
                .willReturn(1);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.verification())
                .contains(new LegacyEventMigration.Check("Rows written", 2, 1));
        assertThat(result.verified())
                .isFalse();
    }
}
