package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Place;
import dev.ted.jittertravel.domain.PrivateEventId;

import java.time.LocalDateTime;

/**
 * One planned private event, as the "match location" page shows it: which evening this is, where it
 * actually is, and where the schedule currently counts it as being.
 * <p>
 * <strong>This is a decision-support surface, not a recording one</strong> (CLAUDE.md, "A recording
 * surface needs no decision-support information", pointing the other way). Ted is here to
 * <em>choose</em> a value, so the page carries what makes that choice answerable — in particular
 * both {@code city} (where the venue is) and {@code locationForMatching} (what the schedule thinks),
 * because the whole point of the page is that those two differ and the second one is wrong.
 * Compare {@link PrivateEventDetailsView}, the cancel page's, which carries identification and
 * consequences only.
 * <p>
 * Deliberately <em>not</em> {@link PrivateEventDetailsView} widened to fit — the same call
 * {@link PlannedPrivateEventView} made, and A5 of {@code docs/ChangePrivateEventPlan.md} calls it a
 * correction rather than a preference. This makes it the second read model in the tree to carry a
 * private event's {@code locationForMatching}, which is honest: two pages genuinely need it.
 * <p>
 * The route is OWNER-only, so there is nothing to redact — {@code PublicCalendarProjector} builds
 * its "Busy" block straight from the event and never meets this type.
 * <p>
 * Times are the venue-zone wall clock (both ends share one zone), for identification only.
 */
public record PrivateEventMatchingLocationView(
        PrivateEventId privateEventId,
        String title,
        String venueName,
        String city,
        String country,
        String locationForMatching,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {

    /**
     * Whether the schedule is already being told something other than the venue's own city — which
     * is exactly when the page has something to explain.
     * <p>
     * Asked through {@link Place#matches} rather than with a hand-written {@code equalsIgnoreCase},
     * so this page and {@code ScheduleGapProjector} cannot answer it differently — a value compared
     * as a city has one normalization and not two. The day {@code Place} is strengthened past
     * {@code trim()} (U+00A0 is the gap CLAUDE.md names), the page that exists to explain the
     * override picks the fix up instead of quietly disagreeing with the schedule it describes.
     */
    public boolean isOverridden() {
        return !new Place(locationForMatching).matches(new Place(city));
    }
}
