package dev.ted.jittertravel.application;

import java.util.ArrayList;
import java.util.List;

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
            // Fields are still named one by one rather than returning `entry`, so a new
            // field cannot ride along unnoticed. Private social events are the separate,
            // redacted PRIVATE_EVENT kind below — never fold them in here.
            case CONFERENCE, GATHERING -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), entry.subTitle(),
                    entry.continuationTitle(), entry.continuationSubTitle(),
                    entry.mapsUrl()
            );
            // A private social event: anonymous viewers see only that Ted is "Busy", when
            // (the time in the event's own zone, via FixedRange), and the city/country — never
            // the title, the venue, or an edit link. See docs/PrivateSocialEventPlan.md and
            // CLAUDE.md. This is the one redacted output that deliberately keeps a
            // ZonedTimestamp (the time is public in its own zone by decision).
            case PRIVATE_EVENT -> redactPrivateEvent(entry);
        };
    }

    /**
     * Rebuilds a private-event entry for anonymous eyes from the owner's
     * {@code [venue?, city, Range]} subtitle: keep the city (the last {@link SubtitleLine.Text}),
     * convert the time {@link SubtitleLine.Range} to a fixed, zone-labelled {@link SubtitleLine.FixedRange},
     * and drop everything else. Never lets the venue, title, or edit link through.
     */
    private CalendarEntry redactPrivateEvent(CalendarEntry entry) {
        List<SubtitleLine> redacted = new ArrayList<>();
        entry.subTitle().stream()
                .filter(SubtitleLine.Range.class::isInstance)
                .map(SubtitleLine.Range.class::cast)
                .findFirst()
                .ifPresent(range -> redacted.add(new SubtitleLine.FixedRange(range.from(), range.to())));
        entry.subTitle().stream()
                .filter(SubtitleLine.Text.class::isInstance)
                .reduce((first, second) -> second)   // the city is the last Text, after any venue
                .ifPresent(redacted::add);
        return new CalendarEntry(
                entry.kind(), entry.start(), entry.end(),
                "Busy", List.copyOf(redacted),
                null, null,
                null
        );
    }
}
