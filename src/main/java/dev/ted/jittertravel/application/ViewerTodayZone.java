package dev.ted.jittertravel.application;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Decides which zone the calendar's and itinerary's notion of <em>today</em> is computed in.
 * <p>
 * "Today" is structural — it drives which day column is highlighted, which past weeks collapse,
 * and the default calendar range — so it must be a single {@link LocalDate} chosen server-side,
 * <em>before</em> the browser-zone script (which only re-localizes displayed <em>time text</em>)
 * ever runs. The server cannot read a browser's zone on its own, so authenticated viewers'
 * browsers report their IANA zone into the {@value #COOKIE_NAME} cookie at login (see
 * {@code ZoneCapturingAuthenticationSuccessHandler}); anonymous viewers never set the cookie and
 * fall back to the configured home zone. Either way the resolution is the same: a valid cookie
 * value wins, anything else (absent, blank, unrecognized) yields the fallback.
 */
public class ViewerTodayZone {

    /** The cookie an authenticated viewer's browser fills with its IANA zone id at login. */
    public static final String COOKIE_NAME = "viewerZone";

    private final ZoneId fallback;

    public ViewerTodayZone(ZoneId fallback) {
        this.fallback = fallback;
    }

    public ZoneId resolve(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(cookieValue);
        } catch (DateTimeException unrecognized) {
            return fallback;
        }
    }
}
