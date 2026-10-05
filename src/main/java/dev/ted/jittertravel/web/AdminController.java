package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.application.ViewerTodayZone;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.EmailTemplates;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    /** The flash attribute that says this page is the one right after a test, so its result flashes. */
    private static final String JUST_TESTED = "justTested";

    /** The test email's words live in {@code src/main/resources/email/test-email.txt}, with the rest. */
    private static final FamilyMessage FAMILY_PROBE_MESSAGE = new EmailTemplates().render("test-email", Map.of());

    private final BackupService backupService;
    private final PostgresPersister persister;
    private final LegacyEventMigration legacyEventMigration;
    private final BackupSource backupSource;
    private final Clock clock;
    private final String feedToken;
    private final String baseUrl;
    private final BrevoEmailClient brevo;
    private final boolean familyNotifyEnabled;
    private final ViewerTodayZone viewerZone;
    private final SettingsReporter settingsReporter = new SettingsReporter();
    private final SendFailureText failureText = new SendFailureText();
    private final FamilyTestMemory testMemory;
    private final Environment environment;
    private final String fallbackZone;

    public AdminController(BackupService backupService, PostgresPersister persister,
                           LegacyEventMigration legacyEventMigration,
                           BackupSource backupSource, Clock clock,
                           BrevoEmailClient brevo, FamilyTestMemory testMemory, Environment environment,
                           @Value("${jittertravel.calendar-feed.token:}") String feedToken,
                           @Value("${jittertravel.base-url:}") String baseUrl,
                           @Value("${jittertravel.family-notify.enabled:false}") boolean familyNotifyEnabled,
                           @Value("${jittertravel.today.fallback-zone:America/Los_Angeles}") String fallbackZone) {
        this.viewerZone = new ViewerTodayZone(ZoneId.of(fallbackZone));
        this.fallbackZone = fallbackZone;
        this.environment = environment;
        this.testMemory = testMemory;
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
    public String adminHome(HttpServletRequest request, Model model,
                            @CookieValue(name = ViewerTodayZone.COOKIE_NAME, required = false) String zoneCookie) {
        return renderHome(request, model, zoneCookie);
    }

    /**
     * Sends one fixed test email through the real client to whatever the recipient is configured as.
     * A wire check, nothing more: it goes through no command and writes no event (a probe subject in
     * the log, and in every backup, forever, for a message that was never about travel), and it
     * ignores the kill switch — the switch governs whether bookings notify, and the probe exists to
     * verify the path <em>before</em> the switch is flipped.
     * <p>
     * The result is remembered in memory until restart and shown in the "Last test email" row of the
     * settings page, which changes state and flashes once, so it cannot be missed. A success redirects
     * (a reload must not resend); a failure re-renders the settings page it was submitted from, so
     * the reason is on a page that can show it.
     */
    @PostMapping("/family-notify/probe")
    public String probeFamilyNotify(HttpServletRequest request, Model model, RedirectAttributes redirectAttributes,
                                    @CookieValue(name = ViewerTodayZone.COOKIE_NAME, required = false) String zoneCookie) {
        if (!brevo.configured()) {
            return renderSettings(request, model, zoneCookie, false);
        }
        try {
            brevo.send(FAMILY_PROBE_MESSAGE);
        } catch (RuntimeException failed) {
            testMemory.failed(clock.instant(), failureText.of(failed));
            return renderSettings(request, model, zoneCookie, true);
        }
        testMemory.succeeded(clock.instant());
        redirectAttributes.addFlashAttribute(JUST_TESTED, true);
        return "redirect:/admin/settings";
    }

    private String renderHome(HttpServletRequest request, Model model, String zoneCookie) {
        model.addAttribute("settingsProblems", settingsReport(request, zoneCookie, false).problems());
        return "admin-home";
    }

    private String renderSettings(HttpServletRequest request, Model model, String zoneCookie, boolean justTested) {
        model.addAttribute("settings", settingsReport(request, zoneCookie, justTested));
        return "admin-settings";
    }

    /**
     * What the running app is using, on a page of its own so {@code /admin} stays a set of cards. The
     * cookie readout lives here now. OWNER-only like everything under {@code /admin}.
     */
    @GetMapping("/settings")
    public String settings(HttpServletRequest request, Model model,
                           @CookieValue(name = ViewerTodayZone.COOKIE_NAME, required = false) String zoneCookie) {
        return renderSettings(request, model, zoneCookie, Boolean.TRUE.equals(model.asMap().get(JUST_TESTED)));
    }

    /**
     * Gathers what the app can read about its own configuration and hands it to
     * {@link SettingsReporter}, reducing every secret to "set" and how it ends on the way. Property
     * names, not constructor values, so a new setting does not widen this class's constructor.
     */
    private SettingsReport settingsReport(HttpServletRequest request, String zoneCookie, boolean justTested) {
        return settingsReporter.report(SettingsReporter.Inputs.builder()
                .familyEnabled(familyNotifyEnabled)
                .familyConfigured(brevo.configured())
                .recipient(orEmpty(brevo.recipient()))
                .replyTo(orEmpty(brevo.replyTo()))
                .brevoKey(settingsReporter.secret(brevo.apiKey(), true))
                .lastTest(testMemory.last())
                .justTested(justTested)
                .zone(viewerZone.resolve(zoneCookie))
                .cookies(new SecureCookieProbe(
                        request.isSecure(), request.getScheme(), request.getHeader("X-Forwarded-Proto")))
                .tedPassword(settingsReporter.secret(environment.getProperty("TED_PASSWORD"), false))
                .familyPassword(settingsReporter.secret(environment.getProperty("FAMILY_PASSWORD"), false))
                .rememberMeKey(settingsReporter.secret(environment.getProperty("REMEMBER_ME_KEY"), false))
                .calendarToken(settingsReporter.secret(feedToken, true))
                .baseUrl(orEmpty(baseUrl))
                .aeroDataBoxKey(settingsReporter.secret(
                        environment.getProperty("jittertravel.aerodatabox.api-key"), true))
                .homeCities(orEmpty(environment.getProperty("jittertravel.home-cities")))
                .fallbackZone(fallbackZone)
                .environment(backupSource.label())
                .build());
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
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
