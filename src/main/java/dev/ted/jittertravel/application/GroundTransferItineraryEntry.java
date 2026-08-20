package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDateTime;

/**
 * A ground transfer on the owner/family itinerary. The itinerary is behind auth (OWNER/FAMILY
 * only), so both ends are named in full — redaction is an anonymous-calendar concern.
 * <p>
 * {@code origin}/{@code destination} arrive already written by {@link TransferEndpointLabel}: an
 * airport code, or the hotel's name. No cities: naming the same journey a second time as cities was
 * noise on the card (Ted, 2026-08-20), and the airport or hotel already says where it is.
 */
public record GroundTransferItineraryEntry(
        String origin,
        String destination,
        ZonedTimestamp departsAt,
        ZonedTimestamp arrivesAt
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.GROUND_TRANSFER; }
    @Override public LocalDateTime anchorTime() { return departsAt.localDateTime(); }
}
