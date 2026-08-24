package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDateTime;

/**
 * A ground transfer on the owner/family itinerary. The itinerary is behind auth (OWNER/FAMILY
 * only), so both ends are named in full — redaction is an anonymous-calendar concern.
 * <p>
 * {@code origin}/{@code destination} arrive already written by {@link TransferEndpointLabel}: an
 * airport code, or the hotel's name. No cities: naming the same journey a second time as cities was
 * noise on the card (Ted, 2026-08-20), and the airport or hotel already says where it is.
 * <p>
 * The id rides along for one reason: the card's OWNER-only cancel link. A transfer has no edit page
 * to deep-link into — correcting one means removing it and entering it again.
 * <p>
 * {@code mode} is free text as Ted typed it — a subway line, a shuttle, who is driving — and blank
 * when he recorded none. It is on the itinerary because this is the card read mid-trip, and it is
 * safe here for the same reason the hotel names above are: the itinerary is behind auth.
 */
public record GroundTransferItineraryEntry(
        GroundTransferId groundTransferId,
        String origin,
        String destination,
        ZonedTimestamp departsAt,
        ZonedTimestamp arrivesAt,
        String mode
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.GROUND_TRANSFER; }
    @Override public LocalDateTime anchorTime() { return departsAt.localDateTime(); }
}
