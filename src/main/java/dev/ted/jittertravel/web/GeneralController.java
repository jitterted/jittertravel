package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import jakarta.annotation.Nullable;
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
    @Nullable
    private final BuildProperties buildProperties;

    GeneralController(PostgresPersister persister,
                      Environment environment,
                      @Nullable BuildProperties buildProperties) {
        this.persister = persister;
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
