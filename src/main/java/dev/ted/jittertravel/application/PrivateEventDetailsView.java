package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PrivateEventId;

import java.time.LocalDateTime;

/**
 * One planned private event, as the cancel confirmation page shows it: which evening is about to be
 * removed, and nothing more. The page is a <em>recording</em> surface — it says what is being
 * cancelled and what that removes, so it carries identification and consequences only (CLAUDE.md,
 * "A recording surface needs no decision-support information").
 * <p>
 * The page is OWNER-only, so there is nothing to redact here — redaction is an anonymous-calendar
 * concern, and the title is exactly what {@code PublicCalendarProjector} never reads.
 * <p>
 * Times are the venue-zone wall clock (both ends share one zone), which is what Ted would read off
 * a clock at the dinner.
 */
public record PrivateEventDetailsView(
        PrivateEventId privateEventId,
        String title,
        String venueName,
        String city,
        String country,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
}
