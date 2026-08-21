package dev.ted.jittertravel.domain;

/**
 * A private social event — a dinner with friends, an evening out — has been planned. Unlike a
 * {@code GatheringPlanned} (a public event Ted attends or speaks at), a private event is redacted
 * for anonymous calendar viewers: they see only "Busy", the time range, and the city/country.
 * See {@code docs/archived/PrivateSocialEventPlan.md} and the redaction rules in CLAUDE.md.
 * <p>
 * Single day, one venue, so {@code startsAt}/{@code endsAt} share one zone. No {@code speaking} or
 * {@code infoUrl} — those are public-event concepts.
 */
public record PrivateEventPlanned(
        PrivateEventId privateEventId,
        String title,
        String venueName,
        Address location,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt
) implements Event {
    public PrivateEventPlanned {
        if (venueName == null) {
            venueName = "";
        }
    }
}
