package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Controller
class GeneralController {

    private static final DateTimeFormatter BUILD_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("America/New_York"));

    private final PostgresPersister persister;
    private final Environment environment;
    private final BuildProperties buildProperties;

    GeneralController(PostgresPersister persister, Environment environment, BuildProperties buildProperties) {
        this.persister = persister;
        this.environment = environment;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        boolean runningLocally = local || environment.acceptsProfiles(Profiles.of("prod-preview"));

        // Nav visibility mirrors the route rules in SecurityConfig (the "!local" chain).
        // In the `local` profile there is no authentication, so show everything.
        boolean owner = local || request.isUserInRole("OWNER");
        boolean family = local || request.isUserInRole("FAMILY");

        boolean showDataEntryNav = owner;
        boolean showBookingsNav = owner;
        boolean showItineraryNav = owner || family;
        // Calendar is always visible (content is redacted for anonymous by CalendarEntryRedactor).

        model.addAttribute("runningLocally", runningLocally);
        model.addAttribute("showDataEntryNav", showDataEntryNav);
        model.addAttribute("showBookingsNav", showBookingsNav);
        model.addAttribute("showItineraryNav", showItineraryNav);
        model.addAttribute("pendingCount", persister.countPendingCommands());
        model.addAttribute("buildTime", BUILD_TIME_FORMATTER.format(buildProperties.getTime()));
        return "index";
    }

    @GetMapping("/read-only")
    public String readOnly() {
        return "read-only";
    }

}
