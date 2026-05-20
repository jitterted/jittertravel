package dev.ted.jittertravel.application;

import java.time.LocalDateTime;

/**
 * One row in the change history shown beneath a {@link BookedFlightView}.
 * <p>
 * The projector pre-formats {@code displayText} for the template, e.g.:
 * <ul>
 *   <li>{@code "Booked on 2026-05-20 12:22PM"} — initial booking entry.</li>
 *   <li>{@code "Changed on 2026-05-20 12:22PM"} — change with no reason.</li>
 *   <li>{@code "Wanted to fly home earlier (changed on 2026-05-20 12:22PM)"} —
 *       change with a reason.</li>
 * </ul>
 * {@code timestamp} is preserved (rather than only the formatted string) so
 * we can later link the row to the underlying event without re-parsing.
 */
public record ChangeEntry(
        LocalDateTime timestamp,
        String displayText
) {
}
