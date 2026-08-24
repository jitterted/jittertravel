package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

/**
 * One planned private event as the {@code /planned-private-events} list shows it: the whole thing,
 * street address included. Mirrors {@link PlannedGatheringView} minus the two public-event
 * components ({@code speaking}, {@code infoUrl}), which a private event has no concept of.
 * <p>
 * This is the <em>only</em> read model that carries a private event's {@code street},
 * {@code region} and {@code postalCode} — the plan form collects them and, until this list, nothing
 * ever read them back. The list route is OWNER-only, which is what makes that fine: redaction is an
 * anonymous-{@code /calendar} concern, and {@link PublicCalendarProjector} builds its "Busy" block
 * from the event directly without ever meeting this type.
 * <p>
 * Deliberately <em>not</em> {@link PrivateEventDetailsView} widened to fit. That record is the
 * cancel page's, and the cancel page is a recording surface that carries identification and nothing
 * more; giving it three fields it must then not render is a value carried and stripped later.
 */
public record PlannedPrivateEventView(
        PrivateEventId privateEventId,
        String title,
        String venueName,
        String street,
        String city,
        String region,
        String postalCode,
        String country,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt
) implements TemporalView {

    /**
     * A private event is "upcoming" until it finishes — an evening already under way is still
     * ahead of you, so the end instant decides, not the start.
     */
    @Override
    public Instant relevantUntil() {
        return endsAt.utc();
    }
}
