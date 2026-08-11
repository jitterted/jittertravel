package dev.ted.jittertravel.application;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Restore is a bulk load that bypasses {@link CommandExecutor}, so it must re-honour the read-only
 * guard the executor would otherwise apply: in read-only mode it refuses up front and touches the
 * database not at all.
 */
class BackupServiceReadOnlyTest {

    @Test
    void restoreInReadOnlyModeRefusesAndWritesNothing() {
        PostgresPersister persister = mock(PostgresPersister.class);
        CommandExecutor readOnly = mock(CommandExecutor.class);
        when(readOnly.isReadOnly()).thenReturn(true);
        BackupService service = new BackupService(persister, readOnly, null, null);

        BackupService.RestoreResult result = service.restoreJson(
                "{\"version\": 2, \"commands\": [], \"events\": []}");

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .singleElement()
                .satisfies(e -> assertThat(e).contains("read-only"));
        verifyNoInteractions(persister);
    }
}
