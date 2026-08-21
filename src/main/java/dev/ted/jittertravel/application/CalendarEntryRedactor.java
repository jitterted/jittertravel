package dev.ted.jittertravel.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Strips private details from calendar entries before they reach an anonymous viewer.
 * <p>
 * Deny-by-default: no branch may return {@code entry} unchanged. Every branch constructs a
 * new {@link CalendarEntry} via the full canonical constructor, naming each field explicitly
 * (including {@code speaking} and {@code editPath}), so adding a field to {@code CalendarEntry}
 * breaks compilation here — forcing a redaction decision — instead of silently publishing the
 * new field. {@code editPath} and {@code cancelPath} are always {@code null}: owner action links
 * are never public — the link itself would tell a stranger the surface exists.
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
                    null, false, null, null, null, null
            );
            case FLIGHT -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), null,
                    entry.continuationTitle(), null,
                    null, false, null, null, null, null
            );
            case TRAIN -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), null,
                    entry.continuationTitle(), null,
                    null, false, null, null, null, null
            );
            // A ground transfer: the taxi from the airport to the hotel. Its owner title names a
            // hotel — "DEN → Marriott Lone Tree" — so unlike FLIGHT and TRAIN above the title
            // cannot be published, and this branch must never be folded into theirs. What an
            // anonymous viewer gets instead is the generic word plus `publicRoute`, the one field
            // the projector fills for exactly this purpose (the owner never sees it rendered).
            // The owner's whole subtitle — the time range — is dropped rather than filtered:
            // redaction rule 2 forbids a ZonedTimestamp surviving on a travel entry, because
            // ZonedTimeTag leaks a clock time in the datetime attribute.
            case GROUND_TRANSFER -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    "🚕 Ground transfer", routeLine(entry.publicRoute()),
                    null, null,
                    null, false, null, null, null, null
            );
            // Conferences are public events: name, venue, location, and times are all visible by
            // decision (Ted attends them publicly). `commitment` is public too — but only because
            // the projector already collapsed every speculative state into WATCHING, so what
            // arrives here cannot distinguish "submitted and waiting" from "hasn't decided". The
            // private half, the AttendanceBasis, never enters a CalendarEntry at all (redaction
            // rule 1 done structurally rather than stripped here).
            // They carry no `speaking` marker today, so it is dropped; when the submission stream
            // lands (docs/ConferenceSubmissionTrackingPlan.md slice 4) this branch gains its own
            // `entry.speaking()` pass-through.
            case CONFERENCE -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), entry.subTitle(),
                    entry.continuationTitle(), entry.continuationSubTitle(),
                    entry.mapsUrl(), false, null, entry.commitment(), null, null
            );
            // Gatherings are public events too, and that Ted is *speaking* at one is public by
            // decision (the venue and time are already public) — so `speaking` passes through to
            // the anonymous calendar. Fields are still named one by one rather than returning
            // `entry`, so a new field cannot ride along unnoticed. Private social events are the
            // separate, redacted PRIVATE_EVENT kind below — never fold them in here.
            case GATHERING -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), entry.subTitle(),
                    entry.continuationTitle(), entry.continuationSubTitle(),
                    entry.mapsUrl(), entry.speaking(), null, null, null, null
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
     * The anonymous ground-transfer subtitle: the publishable route and nothing else. Built from
     * {@code publicRoute} rather than filtered out of the owner's subtitle, so a line added to that
     * subtitle later cannot ride along — the owner's version is discarded wholesale.
     */
    private List<SubtitleLine> routeLine(String publicRoute) {
        return publicRoute == null || publicRoute.isBlank()
                ? List.of()
                : List.of(new SubtitleLine.Text(publicRoute));
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
                null, false, null, null, null, null
        );
    }
}
