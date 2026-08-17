package dev.ted.jittertravel.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the custom login form (templates/login.html), replacing Spring's generated page so the
 * form can carry a hidden {@code browserZone} field. That field lets
 * {@code ZoneCapturingAuthenticationSuccessHandler} learn the viewer's zone at the moment of
 * login — before the first authenticated render — so a deep link that bounced through login shows
 * the correct "today" on first paint.
 * <p>
 * The {@code ?error} and {@code ?logout} flags Spring Security appends are read straight from the
 * request params in the template.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
