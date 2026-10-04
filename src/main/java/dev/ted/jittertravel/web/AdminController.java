package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final FamilyMessage FAMILY_PROBE_MESSAGE = new FamilyMessage(
            "JitterTravel test email",
            "This is a test from JitterTravel, sent from its admin page to check that email reaches "
            + "this inbox. Nothing was booked and nothing needs doing; you can ignore it.");

    private final BackupService backupService;
    private final PostgresPersister persister;
    private final LegacyEventMigration legacyEventMigration;
    private final BackupSource backupSource;
    private final Clock clock;
    private final String feedToken;
    private final String baseUrl;
    private final BrevoEmailClient brevo;
    private final boolean familyNotifyEnabled;

    public AdminController(BackupService backupService, PostgresPersister persister,
                           LegacyEventMigration legacyEventMigration,
                           BackupSource backupSource, Clock clock,
                           BrevoEmailClient brevo,
                           @Value("${jittertravel.calendar-feed.token:}") String feedToken,
                           @Value("${jittertravel.base-url:}") String baseUrl,
                           @Value("${jittertravel.family-notify.enabled:false}") boolean familyNotifyEnabled) {
        this.backupService = backupService;
        this.persister = persister;
        this.legacyEventMigration = legacyEventMigration;
        this.backupSource = backupSource;
        this.clock = clock;
        this.brevo = brevo;
        this.feedToken = feedToken;
        this.baseUrl = baseUrl;
        this.familyNotifyEnabled = familyNotifyEnabled;
    }

    @GetMapping("")
    public String adminHome(HttpServletRequest request, Model model) {
        model.addAttribute("secureCookieProbe", new SecureCookieProbe(
                request.isSecure(), request.getScheme(), request.getHeader("X-Forwarded-Proto")));
        model.addAttribute("familyNotify",
                new FamilyNotifyStatus(familyNotifyEnabled, brevo.configured(), brevo.recipient()));
        return "admin-home";
    }

    /**
     * Sends one fixed test email through the real client to whatever the recipient is configured as,
     * and says where it went. A wire check, nothing more: it goes through no command and writes no
     * event (a probe subject in the log, and in every backup, forever, for a message that was never
     * about travel), and it ignores the kill switch — the switch governs whether bookings notify,
     * and the probe exists to verify the path <em>before</em> the switch is flipped.
     * <p>
     * A failure re-renders the admin page it was submitted from rather than redirecting, so the
     * error is on the page that can show it.
     */
    @PostMapping("/family-notify/probe")
    public String probeFamilyNotify(HttpServletRequest request, Model model,
                                    RedirectAttributes redirectAttributes) {
        if (!brevo.configured()) {
            adminHome(request, model);
            model.addAttribute("familyProbeError",
                    "Not sent: there is no API key or no recipient configured.");
            return "admin-home";
        }
        String recipient = brevo.recipient();
        try {
            brevo.send(FAMILY_PROBE_MESSAGE);
        } catch (RuntimeException failed) {
            adminHome(request, model);
            model.addAttribute("familyProbeError",
                    "Could not send the test email to " + recipient + ": " + failed.getMessage());
            return "admin-home";
        }
        redirectAttributes.addFlashAttribute("familyProbeMessage",
                "Test email accepted by Brevo for " + recipient + ". Check that inbox, and spam.");
        return "redirect:/admin";
    }

    /**
     * The OWNER-only calendar-feed card: shows the subscribe + probe links, each carrying the token
     * (hence OWNER-only, under {@code /admin/**}). When no token is configured the feed is disabled
     * and the page says so instead of showing links.
     */
    @GetMapping("/calendar-feed")
    public String calendarFeed(HttpServletRequest request, Model model) {
        boolean feedEnabled = !feedToken.isBlank();
        model.addAttribute("feedEnabled", feedEnabled);
        if (feedEnabled) {
            String effectiveBaseUrl = baseUrl.isBlank() ? requestBaseUrl(request) : baseUrl;
            model.addAttribute("links", new CalendarFeedLinks(effectiveBaseUrl, feedToken));
        }
        return "admin-calendar-feed";
    }

    private String requestBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    @GetMapping("/restore")
    public String restoreForm() {
        return "admin-restore";
    }

    @PostMapping("/restore")
    public String restore(@RequestParam("content") String content, Model model) {
        BackupService.RestoreResult result = backupService.restoreJson(content);
        if (!result.hasErrors()) {
            model.addAttribute("restoredCommands", result.restoredCommands());
            model.addAttribute("restoredEvents", result.restoredEvents());
            model.addAttribute("skippedCommands", result.skippedCommands());
            model.addAttribute("skippedEvents", result.skippedEvents());
            return "admin-restore-success";
        }
        model.addAttribute("errors", result.errors());
        model.addAttribute("content", content);
        return "admin-restore";
    }

    @PostMapping("/restore/validate")
    public String validateBackup(@RequestParam("content") String content, Model model) {
        BackupService.ValidationReport report = backupService.validateJson(content);
        model.addAttribute("errors", report.errors());
        model.addAttribute("validatedCommands", report.hasErrors() ? null : report.validCommandCount());
        model.addAttribute("validatedEvents", report.hasErrors() ? null : report.validEventCount());
        model.addAttribute("content", content);
        return "admin-restore";
    }

    @GetMapping("/database")
    public String database(Model model) {
        List<PostgresPersister.TableStat> stats = persister.tableStats();
        model.addAttribute("stats", stats);
        model.addAttribute("allEmpty", stats.stream().allMatch(s -> s.rowCount() == 0));
        return "admin-database";
    }

    @PostMapping("/database/truncate")
    public String truncate(@RequestParam("confirm") String confirm, Model model,
                           RedirectAttributes redirectAttributes) {
        if (!"DELETE".equals(confirm)) {
            List<PostgresPersister.TableStat> stats = persister.tableStats();
            model.addAttribute("stats", stats);
            model.addAttribute("allEmpty", stats.stream().allMatch(s -> s.rowCount() == 0));
            model.addAttribute("error", "You must type DELETE exactly to confirm truncation.");
            return "admin-database";
        }
        persister.truncateAllTables();
        redirectAttributes.addFlashAttribute("truncated", true);
        return "redirect:/admin/database";
    }

    @GetMapping("/migrate-legacy-events")
    public String migrateLegacyEventsForm(Model model) {
        model.addAttribute("report", legacyEventMigration.preview());
        return "admin-migrate-legacy-events";
    }

    /**
     * Destructive and one-way (renaming a type is not undone by rolling the code back), so it takes
     * the same typed confirmation as truncation rather than a bare button — see the destructive-action
     * guideline in CLAUDE.md.
     */
    @PostMapping("/migrate-legacy-events")
    public String migrateLegacyEvents(@RequestParam(value = "confirm", required = false) String confirm,
                                      Model model) {
        if (!"MIGRATE".equals(confirm)) {
            model.addAttribute("confirmError", "You must type MIGRATE exactly to confirm the migration.");
            model.addAttribute("report", legacyEventMigration.preview());
            return "admin-migrate-legacy-events";
        }
        LegacyEventMigration.MigrationResult result = legacyEventMigration.migrate();
        model.addAttribute("result", result);
        // Re-preview so the page shows the (now-settled) state whether the run applied or was refused.
        model.addAttribute("report", legacyEventMigration.preview());
        return "admin-migrate-legacy-events";
    }

    @GetMapping("/backup")
    public ResponseEntity<String> backup() {
        BackupService.Backup backup =
                backupService.createBackup(OffsetDateTime.now(clock), backupSource.label());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(backup.filename()).build().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(backup.json());
    }
}
