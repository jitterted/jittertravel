package dev.ted.jittertravel.application;

/**
 * Strips private details from calendar entries before they reach an anonymous viewer.
 * <p>
 * Deny-by-default: no branch may return {@code entry} unchanged. Every branch constructs a
 * new {@link CalendarEntry} naming each field explicitly, so adding a field to
 * {@code CalendarEntry} breaks compilation here — forcing a redaction decision — instead of
 * silently publishing the new field. All branches use the 8-argument constructor, which
 * drops {@code editPath}: owner edit links are never public.
 * <p>
 * See "Redaction: anonymous viewers are a first-class threat model" in CLAUDE.md.
 */
public class CalendarEntryRedactor {

    public CalendarEntry redact(CalendarEntry entry) {
        return switch (entry.kind()) {
            case LODGING -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    "Hotel", entry.subTitle(),
                    "Hotel cont'd", entry.continuationSubTitle(),
                    null
            );
            case FLIGHT -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), null,
                    entry.continuationTitle(), null,
                    null
            );
            case TRAIN -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), null,
                    entry.continuationTitle(), null,
                    null
            );
            // Conferences and gatherings are public events: name, venue, location, and
            // times are all visible by decision (Ted speaks at or attends them publicly).
            // Private social events are a separate, not-yet-modelled entry kind — see
            // docs/Cleanup_Tasks.md. Fields are still named one by one rather than
            // returning `entry`, so a new field cannot ride along unnoticed.
            case CONFERENCE, GATHERING -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), entry.subTitle(),
                    entry.continuationTitle(), entry.continuationSubTitle(),
                    entry.mapsUrl()
            );
        };
    }
}
