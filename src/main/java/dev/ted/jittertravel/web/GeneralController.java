package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
class GeneralController {

    private final PostgresPersister persister;
    private final Environment environment;

    GeneralController(PostgresPersister persister, Environment environment) {
        this.persister = persister;
        this.environment = environment;
    }

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        boolean local = activeProfiles.contains("local");
        boolean runningLocally = local || activeProfiles.contains("prod-preview");

        // In the `local` profile there is no authentication (everything is permitted), so treat
        // it as full access. Otherwise the data-entry/admin nav is shown only when logged in.
        boolean showDataEntryNav = local || isAuthenticated(authentication);

        model.addAttribute("runningLocally", runningLocally);
        model.addAttribute("showDataEntryNav", showDataEntryNav);
        model.addAttribute("pendingCount", persister.countPendingCommands());
        return "index";
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @GetMapping("/read-only")
    public String readOnly() {
        return "read-only";
    }

}
