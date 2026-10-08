package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.application.LocationDataCorrections;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Tag;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@Tag("spring")
@WebMvcTest(AdminController.class)
@WithMockUser(roles = "OWNER")
class AdminControllerTest {

    private static final LocationDataCorrections.Approved ST_RAPHAEL = new LocationDataCorrections.Approved(
            UUID.fromString("c1598660-df8b-4139-af16-2bdc49dca1e8"), "Hotel Best Western Plus St. Raphael",
            List.of(new LocationDataCorrections.FieldChange("city", "St. Georg", "Hamburg"),
                    new LocationDataCorrections.FieldChange("region", "Hamburg", "St. Georg")));
    private static final LocationDataCorrections.Approved DEVNEXUS = new LocationDataCorrections.Approved(
            UUID.fromString("3f43b7c1-1201-4411-8ed9-a69bbba8fc2f"), "Devnexus 2027",
            List.of(new LocationDataCorrections.FieldChange("region", "", "GA")));
    private static final LegacyEventMigration.AirportEndLine DEN_END = new LegacyEventMigration.AirportEndLine(
            132, new LocationDataCorrections.AirportEndFill("DEN", "CO", "US", true));

    // Corrections before a run, all still to make; and after one, all already made.
    private static final LegacyEventMigration.CorrectionsCheck FIXES_PENDING = new LegacyEventMigration.CorrectionsCheck(
            List.of(new LegacyEventMigration.CorrectionLine(58L, ST_RAPHAEL, LegacyEventMigration.CorrectionState.WILL_BE_MADE),
                    new LegacyEventMigration.CorrectionLine(98L, DEVNEXUS, LegacyEventMigration.CorrectionState.WILL_BE_MADE)),
            List.of(DEN_END));
    private static final LegacyEventMigration.CorrectionsCheck FIXES_SETTLED = new LegacyEventMigration.CorrectionsCheck(
            List.of(new LegacyEventMigration.CorrectionLine(58L, ST_RAPHAEL, LegacyEventMigration.CorrectionState.ALREADY_MADE),
                    new LegacyEventMigration.CorrectionLine(98L, DEVNEXUS, LegacyEventMigration.CorrectionState.ALREADY_MADE)),
            List.of());
    private static final List<LegacyEventMigration.Check> VERIFIED = List.of(
            new LegacyEventMigration.Check("Rows written", 5, 5),
            new LegacyEventMigration.Check("Corrections now in the database", 2, 2));

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
    LegacyEventMigration legacyEventMigration;
    @MockitoBean
    BrevoEmailClient brevoEmailClient;
    @MockitoBean
    FamilyTestMemory familyTestMemory;

    @Test
    void databasePageRendersTableStats() {
        given(persister.tableStats()).willReturn(List.of(
                new PostgresPersister.TableStat("event_log", 42),
                new PostgresPersister.TableStat("command_log", 7)));

        assertThat(mockMvc.get().uri("/admin/database"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
                .bodyText()
                .contains("event_log")
                .contains("command_log");
    }

    @Test
    void truncationRestartWarningIsAmberNotGreen() {
        given(persister.tableStats()).willReturn(List.of(
                new PostgresPersister.TableStat("event_log", 0)));

        assertThat(mockMvc.get().uri("/admin/database").flashAttr("truncated", true))
                .hasStatusOk()
                .bodyText()
                // The post-truncate restart warning keeps its icon and its instruction …
                .contains("⚠")
                .contains("Restart the application before importing")
                // … and is now amber. --warning-bg is used only by that warning's rule, and no
                // green background remains on it (the old style was background: var(--success)).
                .contains("var(--warning-bg)")
                .doesNotContain("background: var(--success);");
    }

    @Test
    void adminHomeMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void settingsReportsAPlainRequestAsNotSecure() {
        // The cookie readout's whole job is to answer, from the deployed app, whether the
        // remember-me and viewerZone cookies actually carry Secure — the one thing a local run
        // cannot tell Ted. It moved from /admin to the settings page on 2026-10-05. A MockMvc request
        // is plain http with no forwarded header, which is the local shape.
        assertThat(mockMvc.get().uri("/admin/settings"))
                .hasStatusOk()
                .bodyText()
                .contains("Cookies on this request are NOT marked Secure.")
                .contains("No usable X-Forwarded-Proto reached the app")
                .contains("<span class=\"mono\">(none)</span>")
                .contains("<span class=\"what\">expected over plain http locally; wrong in production</span>");
    }

    @Test
    void settingsReportsASecureRequestAsProtected() {
        assertThat(mockMvc.get().uri("/admin/settings").secure(true))
                .hasStatusOk()
                .bodyText()
                .contains("Cookies on this request are marked Secure, as desired.")
                .contains("The remember-me and viewerZone cookies are protected in transit, as expected.")
                .doesNotContain("NOT marked Secure");
    }

    @Test
    void settingsCallsTheConsumedForwardedHeaderCorrectRatherThanLeavingItLookingLikeAGap() {
        // The reading Ted is most likely to misread on a healthy deploy: "(none)" beside two
        // green values looks like a gap, and is in fact the evidence that ForwardedHeaderFilter
        // applied the header and then consumed it. The page has to say which.
        assertThat(mockMvc.get().uri("/admin/settings").secure(true))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"mono\">(none)</span>")
                .contains("<span class=\"what\">this is correct — the header was consumed after being "
                          + "applied, which is what the strategy does</span>")
                .contains("<span class=\"what\">working as desired — this is the value that marks "
                          + "both cookies</span>");
    }

    @Test
    void theCookieReadoutIsNoLongerOnTheAdminHomePage() {
        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("Cookies on this request")
                .doesNotContain("X-Forwarded-Proto");
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
    void migrateLegacyEventsFormRendersThePreviewCounts() {
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 4, 5, 5, List.of(), FIXES_PENDING));

        assertThat(mockMvc.get().uri("/admin/migrate-legacy-events"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
                .bodyText()
                .contains("Events scanned")
                .contains("Types to rename")
                .contains("Rows to write")
                .contains("Renaming types is one-way.")
                .contains("Run migration");
    }

    @Test
    void migrateLegacyEventsPageLinksBackToTheSiteHomeAndNotJustAdmin() {
        // Reached from the post-deploy task banner, so it needs a way back to the app rather than
        // only up to /admin (Ted, 2026-08-19).
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 4, 5, 5, List.of(), FIXES_PENDING));

        assertThat(mockMvc.get().uri("/admin/migrate-legacy-events"))
                .hasStatusOk()
                .bodyText()
                .contains("<a href=\"/\">JitterTravel</a>")
                .contains("<a href=\"/admin\">");
    }

    @Test
    void migrateLegacyEventsFormWithNothingToRenameOmitsTheOneWayWarning() {
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 0, 5, 5, List.of(), FIXES_PENDING));

        assertThat(mockMvc.get().uri("/admin/migrate-legacy-events"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("Renaming types is one-way.");
    }

    @Test
    void migrateLegacyEventsPostRunsMigrationAndShowsResult() {
        given(legacyEventMigration.migrate()).willReturn(
                new LegacyEventMigration.MigrationResult(false, 3, 2, 4, List.of(), VERIFIED));
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 0, 0, 0, 0, 10, List.of(), FIXES_SETTLED));

        assertThat(mockMvc.post().uri("/admin/migrate-legacy-events").with(csrf()).param("confirm", "MIGRATE"))
                .hasStatusOk()
                .bodyText()
                .contains("<strong>✓ Migration complete and verified.</strong>")
                .contains("3 payloads rewritten, 2 stamps added, 4 types renamed")
                .contains("Renamed rows make this database new-build-only.")
                .contains("<td>Corrections now in the database</td>")
                .contains("<td class=\"match\">✓ match</td>")
                .doesNotContain("<td class=\"mismatch\">");
        verify(legacyEventMigration).migrate();
    }

    @Test
    void migrateLegacyEventsPostWhoseReReadDoesNotMatchIsNeverShownAsComplete() {
        given(legacyEventMigration.migrate()).willReturn(
                new LegacyEventMigration.MigrationResult(false, 3, 2, 0, List.of(), List.of(
                        new LegacyEventMigration.Check("Rows written", 5, 5),
                        new LegacyEventMigration.Check("Corrections now in the database", 10, 9))));
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 0, 0, 0, 0, 10, List.of(), FIXES_SETTLED));

        assertThat(mockMvc.post().uri("/admin/migrate-legacy-events").with(csrf()).param("confirm", "MIGRATE"))
                .hasStatusOk()
                .bodyText()
                .contains("<strong>✗ Migration written, but the database does not hold what was expected.</strong>")
                .doesNotContain("Migration complete")
                .contains("<td class=\"num\">9</td>")
                .contains("<td class=\"mismatch\">✗ mismatch</td>");
    }

    @Test
    void migrateLegacyEventsFormListsEveryCorrectionWithWhatThisRunWillDo() {
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 0, 5, 5, List.of(), FIXES_PENDING));

        assertThat(mockMvc.get().uri("/admin/migrate-legacy-events"))
                .hasStatusOk()
                .bodyText()
                .contains("<strong>✓ All 2 approved corrections accounted for</strong>")
                .contains("<span>This migration will make 2. An earlier run made 0. None will be skipped.</span>")
                .contains("<td class=\"ev\">#58</td>")
                .contains("<span>Hotel Best Western Plus St. Raphael</span>")
                .contains("<span>city </span><del>St. Georg</del><span> → Hamburg, </span>")
                .contains("<del>(none)</del><span> → GA</span>")
                .contains("<span class=\"pill will\">Will be made</span>")
                .contains("<td class=\"ev\">#132</td>")
                .contains("<div class=\"chg\">→ CO, US</div>")
                .contains("<span class=\"pill will\">Will be filled in</span>");
    }

    @Test
    void migrateLegacyEventsFormNamesASkippedCorrectionAndWhy() {
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 0, 5, 5, List.of(),
                        new LegacyEventMigration.CorrectionsCheck(List.of(
                                new LegacyEventMigration.CorrectionLine(58L, ST_RAPHAEL,
                                        LegacyEventMigration.CorrectionState.VALUES_CHANGED),
                                new LegacyEventMigration.CorrectionLine(null, DEVNEXUS,
                                        LegacyEventMigration.CorrectionState.EVENT_NOT_FOUND)),
                                List.of())));

        assertThat(mockMvc.get().uri("/admin/migrate-legacy-events"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"banner warning\">")
                .contains("<strong>⚠ 0 of 2 approved corrections accounted for</strong>")
                .contains("<span>This migration will make 0. An earlier run made 0. 2 will be skipped. Its row below says why.</span>")
                .contains("<span class=\"pill skip\">Skipped: values changed since approval</span>")
                .contains("<td class=\"ev\">—</td>")
                .contains("<span class=\"pill skip\">Skipped: event not in this database</span>");
    }

    @Test
    void migrateLegacyEventsFormAfterARunShowsEveryCorrectionAlreadyMade() {
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 0, 0, 0, 0, 10, List.of(), FIXES_SETTLED));

        assertThat(mockMvc.get().uri("/admin/migrate-legacy-events"))
                .hasStatusOk()
                .bodyText()
                .contains("<strong>✓ All 2 approved corrections accounted for</strong>")
                .contains("<span>This migration will make 0. An earlier run made 2. None will be skipped.</span>")
                .contains("<span class=\"pill done\">Already made</span>")
                .doesNotContain("<span class=\"pill will\">");
    }

    @Test
    void migrateLegacyEventsPostWithNoRenamesOmitsTheRollbackWarning() {
        given(legacyEventMigration.migrate()).willReturn(
                new LegacyEventMigration.MigrationResult(false, 3, 2, 0, List.of(), VERIFIED));
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 0, 0, 0, 0, 10, List.of(), FIXES_SETTLED));

        assertThat(mockMvc.post().uri("/admin/migrate-legacy-events").with(csrf()).param("confirm", "MIGRATE"))
                .hasStatusOk()
                .bodyText()
                .contains("0 types renamed")
                .doesNotContain("Renamed rows make this database new-build-only.");
    }

    @Test
    void migrateLegacyEventsPostRefusedInReadOnlyShowsRefusal() {
        given(legacyEventMigration.migrate()).willReturn(
                new LegacyEventMigration.MigrationResult(true, 0, 0, 0,
                        List.of("Migration refused: the application is in read-only mode, so nothing was written."),
                        List.of()));
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 0, 5, 5, List.of(), FIXES_PENDING));

        assertThat(mockMvc.post().uri("/admin/migrate-legacy-events").with(csrf()).param("confirm", "MIGRATE"))
                .hasStatusOk()
                .bodyText()
                .contains("Migration refused")
                .contains("read-only");
    }

    @Test
    void migrateLegacyEventsPostWithoutTheConfirmWordRunsNothing() {
        // Destructive and one-way, so it takes a typed confirmation like truncation does.
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 4, 5, 5, List.of(), FIXES_PENDING));

        assertThat(mockMvc.post().uri("/admin/migrate-legacy-events").with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("You must type MIGRATE exactly to confirm the migration.");

        verify(legacyEventMigration, never()).migrate();
    }

    @Test
    void migrateLegacyEventsPostWithTheWrongConfirmWordRunsNothing() {
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 4, 5, 5, List.of(), FIXES_PENDING));

        assertThat(mockMvc.post().uri("/admin/migrate-legacy-events").with(csrf())
                .param("confirm", "migrate"))
                .hasStatusOk()
                .bodyText()
                .contains("You must type MIGRATE exactly to confirm the migration.");

        verify(legacyEventMigration, never()).migrate();
    }

    @Test
    void migrateLegacyEventsPageWarnsInRedAndOffersARedButtonBehindATypedConfirmation() {
        // Irreversible actions are red and gated; amber is for the recoverable kind (CLAUDE.md).
        given(legacyEventMigration.preview()).willReturn(
                new LegacyEventMigration.MigrationReport(10, 3, 2, 4, 5, 5, List.of(), FIXES_PENDING));

        assertThat(mockMvc.get().uri("/admin/migrate-legacy-events"))
                .hasStatusOk()
                .bodyText()
                .contains("class=\"danger-note\"")
                .contains("placeholder=\"Type MIGRATE\"")
                .contains("background: var(--danger); color: #fff;")
                .doesNotContain("class=\"backup-note\"");
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
    void restoreSuccessShowsRestartWarningAndNoNavigationLinks() {
        given(backupService.restoreJson(anyString()))
                .willReturn(new BackupService.RestoreResult(3, 0, 5, 0, List.of()));

        assertThat(mockMvc.post().uri("/admin/restore")
                .with(csrf())
                .param("content", "{}"))
                .hasStatusOk()
                .bodyText()
                // The restart instruction is the whole point of the page, so it carries a warning icon.
                .contains("⚠")
                .contains("Restart the application")
                // Navigation off the page is deliberately removed so the warning can't be skipped past.
                .doesNotContain("/admin/commandlog")
                .doesNotContain("View command log")
                .doesNotContain("class=\"btn");
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
