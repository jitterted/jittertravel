package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CommandImporter;
import dev.ted.jittertravel.application.ConferenceMigrationService;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AdminController.class)
@WithMockUser(roles = "OWNER")
class AdminControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    CommandImporter commandImporter;
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
    void importFormMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/admin/import"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
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
    void importSuccessRendersSuccessView() {
        given(commandImporter.importJson(anyString()))
                .willReturn(new CommandImporter.ImportResult(3, 0, List.of()));

        assertThat(mockMvc.post().uri("/admin/import")
                .with(csrf())
                .param("content", "[]"))
                .hasStatusOk()
                .bodyText()
                .contains("3");
    }

    @Test
    void importSuccessViewReportsSkippedAlreadyImportedCommands() {
        given(commandImporter.importJson(anyString()))
                .willReturn(new CommandImporter.ImportResult(1, 2, List.of()));

        assertThat(mockMvc.post().uri("/admin/import")
                .with(csrf())
                .param("content", "[]"))
                .hasStatusOk()
                .bodyText()
                .contains("Skipped 2 commands already in the log.");
    }

    @Test
    void importWithErrorsRedisplaysFormWithErrors() {
        given(commandImporter.importJson(anyString()))
                .willReturn(new CommandImporter.ImportResult(0, 0, List.of("Failed to import Foo: boom")));

        assertThat(mockMvc.post().uri("/admin/import")
                .with(csrf())
                .param("content", "bad json"))
                .hasStatusOk()
                .bodyText()
                .contains("Failed to import Foo: boom");
    }

    @Test
    void validateOnlyReportsProblemsAndNeverCallsImport() {
        given(commandImporter.validateJson(anyString()))
                .willReturn(new CommandImporter.ValidationReport(0, List.of("Entry 47: no zone for Lone Tree")));

        assertThat(mockMvc.post().uri("/admin/import/validate")
                .with(csrf())
                .param("content", "[]"))
                .hasStatusOk()
                .bodyText()
                .contains("Entry 47: no zone for Lone Tree");
        verify(commandImporter, never()).importJson(anyString());
    }

    @Test
    void validateOnlyOfACleanFileSaysNothingWasWritten() {
        given(commandImporter.validateJson(anyString()))
                .willReturn(new CommandImporter.ValidationReport(12, List.of()));

        assertThat(mockMvc.post().uri("/admin/import/validate")
                .with(csrf())
                .param("content", "[]"))
                .hasStatusOk()
                .bodyText()
                .contains("all 12 entries would import", "Nothing was written");
        verify(commandImporter, never()).importJson(anyString());
    }

    @Test
    void validateOnlyKeepsTheSubmittedContentInTheTextarea() {
        given(commandImporter.validateJson(anyString()))
                .willReturn(new CommandImporter.ValidationReport(0, List.of("boom")));

        assertThat(mockMvc.post().uri("/admin/import/validate")
                .with(csrf())
                .param("content", "[{\"type\": \"MarkerForTheTextarea\"}]"))
                .hasStatusOk()
                .bodyText()
                .as("the file must survive the round trip so it can be fixed in place")
                .contains("MarkerForTheTextarea");
    }
}
