package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.BackupService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pre-deploy boot-replay preflight (see {@code docs/archived/LegacyEventEagerMigrationPlan.md} and the
 * Cleanup-Tasks item). Replays every event in a <em>production copy</em> through the exact read path
 * boot uses — {@link EventPayloadUpcaster#upcast} → {@link EventTypes#classFor} → bind, including zone
 * resolution — and fails if any row cannot be read, <em>before</em> that build is deployed. This is
 * what caught the 2026-08-16 Morocco/Antwerp failures; {@code /admin/zone-audit} is not a substitute
 * (runtime-only, went stale).
 *
 * <p><strong>How to run.</strong> Point it at a backup JSON taken from the target instance
 * (Admin → Backup) and run the {@code replay-preflight} tier:
 * <pre>
 *   ./mvnw test -Preplay-preflight -Dpreflight.dump=/path/to/jittertravel-backup-production-….json
 * </pre>
 * With no {@code -Dpreflight.dump} the test skips, so it is inert in the normal build. It is tagged
 * {@code replay-preflight} and excluded from the default build (which never has a prod dump to hand).
 *
 * <p><strong>Why restore is the preflight.</strong> {@link BackupService#restoreJson} validates every
 * event payload by upcasting a copy and binding it — the same operation boot performs — before it
 * writes anything, then this test additionally drives {@link PostgresPersister#loadAllEvents()} over
 * the freshly-loaded rows, exercising the real Postgres JSONB round-trip end to end. A bad legacy row
 * surfaces as a restore error naming the row, exactly as it would abort boot.
 */
@Tag("replay-preflight")
@SpringBootTest
class BootReplayPreflightTest extends AbstractTestcontainerIntegrationTest {

    @Autowired BackupService backupService;
    @Autowired PostgresPersister persister;

    @Test
    void everyStoredEventInAProductionDumpReplaysCleanly() throws Exception {
        String dumpPath = System.getProperty("preflight.dump");
        assumeTrue(dumpPath != null && !dumpPath.isBlank(),
                "Set -Dpreflight.dump=/path/to/backup.json to run the boot-replay preflight against a prod copy");

        String dumpJson = Files.readString(Path.of(dumpPath));

        persister.truncateAllTables();
        BackupService.RestoreResult result = backupService.restoreJson(dumpJson);

        // Restore's validate pass upcasts+binds every event exactly as boot would; a row that would
        // abort the boot replay is reported here by sequence and type, and nothing is written.
        assertThat(result.hasErrors())
                .as("rows that would break the boot replay of this build on that data: %s", result.errors())
                .isFalse();

        // Belt and suspenders: drive the real boot read path over the now-loaded rows.
        assertThat(persister.loadAllEvents())
                .as("the loaded dump replays through the real read path without throwing")
                .isNotEmpty();
    }
}
