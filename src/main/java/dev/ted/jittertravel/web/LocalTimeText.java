package dev.ted.jittertravel.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A moment as the viewer reads it: "3:42 PM PDT". The meridiem is written by hand because current
 * JDKs put a narrow no-break space before it in the US pattern, which shows as a stray character.
 */
final class LocalTimeText {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm", Locale.US);
    private static final DateTimeFormatter ZONE = DateTimeFormatter.ofPattern("zzz", Locale.US);

    String when(Instant instant, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        return CLOCK.format(local) + (local.getHour() < 12 ? " AM " : " PM ") + ZONE.format(local);
    }
}
