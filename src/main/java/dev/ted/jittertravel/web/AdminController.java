package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CommandImporter;
import dev.ted.jittertravel.application.ConferenceMigrationService;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceSpansMultipleDays;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
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

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final CommandImporter commandImporter;
    private final PostgresPersister persister;
    private final TentativeConferenceProjector tentativeConferenceProjector;
    private final ConferenceMigrationService conferenceMigrationService;

    public AdminController(CommandImporter commandImporter, PostgresPersister persister,
                           TentativeConferenceProjector tentativeConferenceProjector,
                           ConferenceMigrationService conferenceMigrationService) {
        this.commandImporter = commandImporter;
        this.persister = persister;
        this.tentativeConferenceProjector = tentativeConferenceProjector;
        this.conferenceMigrationService = conferenceMigrationService;
    }

    @GetMapping("")
    public String adminHome() {
        return "admin-home";
    }

    @GetMapping("/import")
    public String importForm() {
        return "admin-import";
    }

    @PostMapping("/import")
    public String importCommands(@RequestParam("content") String content, Model model) {
        CommandImporter.ImportResult result = commandImporter.importJson(content);
        if (!result.hasErrors()) {
            model.addAttribute("importedCount", result.importedCount());
            return "admin-import-success";
        }
        model.addAttribute("errors", result.errors());
        model.addAttribute("content", content);
        return "admin-import";
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

    @GetMapping("/migrate-conferences")
    public String migrateConferencesForm(Model model) {
        model.addAttribute("conferences", tentativeConferenceProjector.views());
        return "admin-migrate-conferences";
    }

    @PostMapping("/migrate-conferences")
    public String migrateConference(@RequestParam UUID conferenceId,
                                    RedirectAttributes redirectAttributes) {
        try {
            conferenceMigrationService.migrateToGathering(ConferenceId.of(conferenceId));
        } catch (ConferenceSpansMultipleDays e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/migrate-conferences";
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportCommands() {
        String json = commandImporter.exportJson();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("commands.json").build().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }
}
