package dev.ted.jittertravel.application;

import dev.ted.jittertravel.infrastructure.EventPayloadUpcaster;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The eager migration rewrites {@code event_log} directly, bypassing {@link CommandExecutor}, so it
 * must re-honour the read-only guard the executor would otherwise apply: in read-only mode it refuses
 * up front and touches the database not at all.
 */
class LegacyEventMigrationReadOnlyTest {

    @Test
    void migrateInReadOnlyModeRefusesAndWritesNothing() {
        PostgresPersister persister = mock(PostgresPersister.class);
        CommandExecutor readOnly = mock(CommandExecutor.class);
        when(readOnly.isReadOnly()).thenReturn(true);
        LegacyEventMigration migration = new LegacyEventMigration(
                persister, mock(EventPayloadUpcaster.class), null, readOnly);

        LegacyEventMigration.MigrationResult result = migration.migrate();

        assertThat(result.refusedReadOnly()).isTrue();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .singleElement()
                .satisfies(e -> assertThat(e).contains("read-only"));
        // Refused before even reading: the persister is never touched.
        verifyNoInteractions(persister);
    }
}
