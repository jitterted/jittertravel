package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.ConferenceMigrationService;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AdminController.class)
@WithMockUser(roles = "OWNER")
class AdminControllerTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-05-31T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        BackupSource backupSource() {
            return new BackupSource("");   // empty marker -> "local"
        }
    }

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    BackupService backupService;
    @MockitoBean
    PostgresPersister persister;
    @MockitoBean
    TentativeConferenceProjector tentativeConferenceProjector;
    @MockitoBean
    ConferenceMigrationService conferenceMigrationService;

    @Test
    void adminHomeMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void restoreFormMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/admin/restore"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void backupDownloadsJsonAsAnAttachment() {
        given(backupService.createBackup(any(), anyString()))
                .willReturn(new BackupService.Backup(
                        "jittertravel-backup-local-2026-05-31T100000Z.json", "{\"version\": 2}"));

        assertThat(mockMvc.get().uri("/admin/backup"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .hasHeader("Content-Disposition",
                        "attachment; filename=\"jittertravel-backup-local-2026-05-31T100000Z.json\"");
    }

    @Test
    void databaseMapsToOkAndRendersTableStats() {
        given(persister.tableStats()).willReturn(List.of(
                new PostgresPersister.TableStat("command_log", 2),
                new PostgresPersister.TableStat("event_log", 5)
        ));

        assertThat(mockMvc.get().uri("/admin/database"))
                .hasStatusOk()
                .bodyText()
                .contains("command_log");
    }

    @Test
    void migrateConferencesFormMapsToOkWithHtmlContentType() {
        given(tentativeConferenceProjector.migratableViews()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/admin/migrate-conferences"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void restoreSuccessRendersSuccessViewWithRestoredCounts() {
        given(backupService.restoreJson(anyString()))
                .willReturn(new BackupService.RestoreResult(3, 0, 5, 0, List.of()));

        assertThat(mockMvc.post().uri("/admin/restore")
                .with(csrf())
                .param("content", "{}"))
                .hasStatusOk()
                .bodyText()
                .contains("Restored 3 command(s) and 5 event(s).");
    }

    @Test
    void restoreSuccessViewReportsSkippedRowsAlreadyPresent() {
        given(backupService.restoreJson(anyString()))
                .willReturn(new BackupService.RestoreResult(1, 2, 4, 3, List.of()));

        assertThat(mockMvc.post().uri("/admin/restore")
                .with(csrf())
                .param("content", "{}"))
                .hasStatusOk()
                .bodyText()
                .contains("Skipped 2 command(s) and 3 event(s) already present.");
    }

    @Test
    void restoreWithErrorsRedisplaysFormWithErrors() {
        given(backupService.restoreJson(anyString()))
                .willReturn(new BackupService.RestoreResult(0, 0, 0, 0, List.of("Event 5 (HotelBooked) payload cannot be restored: boom")));

        assertThat(mockMvc.post().uri("/admin/restore")
                .with(csrf())
                .param("content", "bad json"))
                .hasStatusOk()
                .bodyText()
                .contains("Event 5 (HotelBooked) payload cannot be restored: boom");
    }

    @Test
    void validateOnlyReportsProblemsAndNeverCallsRestore() {
        given(backupService.validateJson(anyString()))
                .willReturn(new BackupService.ValidationReport(0, 0, List.of("Event 47 references command X, which is not in the backup")));

        assertThat(mockMvc.post().uri("/admin/restore/validate")
                .with(csrf())
                .param("content", "{}"))
                .hasStatusOk()
                .bodyText()
                .contains("Event 47 references command X, which is not in the backup");
        verify(backupService, never()).restoreJson(anyString());
    }

    @Test
    void validateOnlyOfACleanFileSaysNothingWasWritten() {
        given(backupService.validateJson(anyString()))
                .willReturn(new BackupService.ValidationReport(12, 7, List.of()));

        assertThat(mockMvc.post().uri("/admin/restore/validate")
                .with(csrf())
                .param("content", "{}"))
                .hasStatusOk()
                .bodyText()
                .contains("12 command(s) and 7 event(s) would restore", "Nothing was written");
        verify(backupService, never()).restoreJson(anyString());
    }

    @Test
    void validateOnlyKeepsTheSubmittedContentInTheTextarea() {
        given(backupService.validateJson(anyString()))
                .willReturn(new BackupService.ValidationReport(0, 0, List.of("boom")));

        assertThat(mockMvc.post().uri("/admin/restore/validate")
                .with(csrf())
                .param("content", "{\"marker\": \"MarkerForTheTextarea\"}"))
                .hasStatusOk()
                .bodyText()
                .as("the file must survive the round trip so it can be fixed in place")
                .contains("MarkerForTheTextarea");
    }
}
