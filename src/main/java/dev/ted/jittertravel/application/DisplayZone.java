package dev.ted.jittertravel.application;

import java.util.Locale;

/**
 * Which zone a rendered time is shown in. Evaluation (past/future, overlap) is never affected —
 * that is always instant-based; this only decides what a viewer reads.
 *
 * <ul>
 *   <li>{@code ENTRY} — the zone of the place the entry happens in (the traveler's own view,
 *       and the server-rendered baseline that works with no JavaScript).</li>
 *   <li>{@code BROWSER} — the viewer's own zone, applied client-side.</li>
 * </ul>
 */
public enum DisplayZone {
    ENTRY,
    BROWSER;

    /**
     * Resolves a {@code ?tz=} request parameter, falling back to {@code ENTRY} when the value is
     * absent or unrecognized — the no-JS baseline is also the safe default. Case-insensitive.
     */
    public static DisplayZone fromParam(String value) {
        if (value == null) {
            return ENTRY;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return ENTRY;
        }
    }

    /** The value this zone is named by in a {@code ?tz=} parameter and in the markup. */
    public String paramValue() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
