package dev.ted.jittertravel.application;

import dev.ted.jittertravel.infrastructure.EventJsonMapperFactory;
import dev.ted.jittertravel.infrastructure.EventPayloadUpcaster;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupEventRow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * The backup document now carries an informational {@code metadata} header (when and from which
 * instance it was taken) and the download is named for the same facts. Both the metadata timestamp
 * and the filename stamp are normalised to UTC, so a backup taken at any offset reads and files the
 * same way.
 */
class BackupServiceTest {

    private final PostgresPersister persister = mock(PostgresPersister.class);
    private final JsonMapper jsonMapper = EventJsonMapperFactory.create();
    private final BackupService backupService = new BackupService(
            persister, mock(CommandExecutor.class), mock(EventPayloadUpcaster.class), jsonMapper);

    BackupServiceTest() {
        given(persister.findAllCommandsForBackup()).willReturn(List.of());
        given(persister.findAllEventsForBackup()).willReturn(List.of());
    }

    @Test
    void metadataRecordsTheSourceAndTheCreatedAtInstant() {
        String json = backupService.backupJson(OffsetDateTime.parse("2026-08-11T14:30:00Z"), "production");

        JsonNode metadata = jsonMapper.readTree(json).get("metadata");
        assertThat(metadata.get("source").asText())
                .isEqualTo("production");
        assertThat(OffsetDateTime.parse(metadata.get("createdAt").asText()).toInstant())
                .isEqualTo(Instant.parse("2026-08-11T14:30:00Z"));
    }

    @Test
    void createdAtAndFilenameAreNormalisedToUtc() {
        // 16:30 at +02:00 is the same instant as 14:30Z
        BackupService.Backup backup =
                backupService.createBackup(OffsetDateTime.parse("2026-08-11T16:30:00+02:00"), "production");

        assertThat(backup.filename())
                .isEqualTo("jittertravel-backup-production-2026-08-11T143000Z.json");
        assertThat(OffsetDateTime.parse(jsonMapper.readTree(backup.json()).get("metadata").get("createdAt").asText()).toInstant())
                .isEqualTo(Instant.parse("2026-08-11T14:30:00Z"));
    }

    @Test
    void filenameCarriesTheSourceAndTheUtcTimestamp() {
        BackupService.Backup backup =
                backupService.createBackup(OffsetDateTime.parse("2026-08-11T14:30:00Z"), "local");

        assertThat(backup.filename())
                .isEqualTo("jittertravel-backup-local-2026-08-11T143000Z.json");
    }

    @Test
    void writesVersion3AndCarriesEachEventSchemaVersionStamp() {
        given(persister.findAllEventsForBackup()).willReturn(List.of(new BackupEventRow(
                1L, UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.parse("2026-08-11T14:30:00Z"), "HotelBooked", "{}", 2)));

        JsonNode root = jsonMapper.readTree(
                backupService.backupJson(OffsetDateTime.parse("2026-08-11T14:30:00Z"), "local"));

        assertThat(root.get("version").asInt())
                .as("current backup format is version 3")
                .isEqualTo(3);
        assertThat(root.get("events").get(0).get("schemaVersion").asInt())
                .as("each event carries its per-type schema-version stamp")
                .isEqualTo(2);
    }
}
