package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The blocking leg, as the form shows it: a sentence and a link to go and look at it.
 * <p>
 * <strong>The link is the point</strong> (Ted, 2026-09-06): wherever the existing entry is named it
 * links to that entry's details page, or its edit page while it has none — which is the case for
 * both flights and trains today. It is a text link and not a pencil, so the "an icon means one
 * thing" rule is untouched, and every form this appears on is OWNER-only.
 * <p>
 * <strong>Why this is not carried in the error message itself.</strong> Thymeleaf escapes
 * {@code th:errors}, so an anchor written into a rejection would render as literal markup. The
 * message stays plain text under the input that fixes it — the departure time, the value the reader
 * would change — and the link rides beside it as its own element.
 * <p>
 * The wording is terse because it sits under an input in a narrow grid column: naming the kind and
 * the time is what tells two similar trips apart, and the link carries the rest.
 */
public record OverlappingLegNotice(String message, String path, String label) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    public static OverlappingLegNotice from(OverlappingLegRefused refusal) {
        ScheduledLeg blocking = refusal.blocking();
        String kind = kindOf(blocking.id());
        return new OverlappingLegNotice(
                "Overlaps a " + kind + " departing " + WHEN.format(blocking.departure().atEntryZone()),
                blocking.id().path(),
                "Open that " + kind);
    }

    /**
     * Exhaustive over {@link ScheduledLegId}, so a third kind of scheduled leg cannot be added
     * without deciding what the reader is told it collided with.
     */
    private static String kindOf(ScheduledLegId id) {
        return switch (id) {
            case ScheduledLegId.Flight ignored -> "flight";
            case ScheduledLegId.Train ignored -> "train";
        };
    }
}
