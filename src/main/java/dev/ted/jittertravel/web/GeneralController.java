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
        boolean isLocalProfile = environment.acceptsProfiles(Profiles.of("local"));
        boolean isRunningLocally = isLocalProfile || environment.acceptsProfiles(Profiles.of("prod-preview"));

        // Nav visibility mirrors the route rules in SecurityConfig (the "!isLocalProfile" chain).
        // In the `isLocalProfile` profile there is no authentication, so show everything.
        boolean isOwner = isLocalProfile || request.isUserInRole("OWNER");
        boolean isFamily = isLocalProfile || request.isUserInRole("FAMILY");

        boolean showItineraryNav = isOwner || isFamily;
        // Calendar is always visible (content is redacted for anonymous by CalendarEntryRedactor).

        model.addAttribute("runningLocally", isRunningLocally);
        model.addAttribute("showDataEntryNav", isOwner);
        model.addAttribute("showBookingsNav", isOwner);
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
