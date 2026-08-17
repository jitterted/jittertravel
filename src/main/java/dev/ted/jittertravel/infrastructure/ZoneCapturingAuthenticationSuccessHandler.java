package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.ViewerTodayZone;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;

/**
 * On a successful login, captures the browser's IANA zone — reported by the login form's hidden
 * {@code browserZone} field — into the {@value ViewerTodayZone#COOKIE_NAME} cookie, then proceeds
 * with the normal saved-request redirect.
 * <p>
 * This is what makes a deep link work through the login bounce: the cookie is set on the very
 * response that redirects to the originally-requested page, so that first authenticated render
 * already knows the viewer's zone and shows the correct "today" — no stale first paint. The
 * cookie is only ever written here, on an authenticated response, so anonymous visitors never
 * receive one (no cookie-consent surface on the public calendar).
 * <p>
 * An absent or unrecognized zone writes no cookie at all; the reader ({@link ViewerTodayZone})
 * then falls back to the configured home zone rather than trusting a bad value.
 */
public class ZoneCapturingAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final int COOKIE_MAX_AGE_SECONDS = (int) Duration.ofDays(400).toSeconds();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        validatedZone(request.getParameter("browserZone"))
                .ifPresent(zone -> response.addCookie(zoneCookie(request, zone)));
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private Optional<ZoneId> validatedZone(String reportedZone) {
        if (reportedZone == null || reportedZone.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(reportedZone));
        } catch (DateTimeException unrecognized) {
            return Optional.empty();
        }
    }

    private Cookie zoneCookie(HttpServletRequest request, ZoneId zone) {
        Cookie cookie = new Cookie(ViewerTodayZone.COOKIE_NAME, zone.getId());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // Read-only over HTTPS in production; over the plain-http local prod-preview it must not
        // be Secure or the browser would drop it.
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        return cookie;
    }
}
