package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Controller
class GeneralController {

    private static final DateTimeFormatter BUILD_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("America/New_York"));

    private final PostgresPersister persister;
    private final ScheduleGapProjector scheduleGapProjector;
    private final EventStore eventStore;
    private final Clock clock;
    private final Environment environment;
    @Nullable
    private final BuildProperties buildProperties;

    GeneralController(PostgresPersister persister,
                      ScheduleGapProjector scheduleGapProjector,
                      EventStore eventStore,
                      Clock clock,
                      Environment environment,
                      @Nullable BuildProperties buildProperties) {
        this.persister = persister;
        this.scheduleGapProjector = scheduleGapProjector;
        this.eventStore = eventStore;
        this.clock = clock;
        this.environment = environment;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        boolean isRunningLocally = environment.acceptsProfiles(Profiles.of("prod-preview"));

        // Nav visibility mirrors the route rules in SecurityConfig: roles come from the
        // authenticated user (locally you log in via the secured chain, just like production).
        boolean isOwner = request.isUserInRole("OWNER");
        boolean isFamily = request.isUserInRole("FAMILY");

        boolean showItineraryNav = isOwner || isFamily;
        // Calendar is always visible (content is redacted for anonymous by CalendarEntryRedactor).

        // Read-only mode means a boot replay or a save failed and writes are now disabled: the
        // page can be silently stale/empty, so surface it to every viewer as a top-of-page banner.
        model.addAttribute("readOnly", eventStore.isReadOnly());
        model.addAttribute("runningLocally", isRunningLocally);
        model.addAttribute("showDataEntryNav", isOwner);
        model.addAttribute("showBookingsNav", isOwner);
        model.addAttribute("showItineraryNav", showItineraryNav);
        // OWNER-only, like the card that reads it (the /schedule-problems report is OWNER-gated).
        // Count only the still-actionable problems (past ones dropped); now captured at the boundary.
        if (isOwner) {
            model.addAttribute("scheduleProblemCount",
                    scheduleGapProjector.problems(Instant.now(clock)).size());
        }
        model.addAttribute("pendingCount", persister.countPendingCommands());
        model.addAttribute("buildTime", BUILD_TIME_FORMATTER.format(buildProperties.getTime()));
        return "index";
    }

    @GetMapping("/read-only")
    public String readOnly() {
        return "read-only";
    }

}
