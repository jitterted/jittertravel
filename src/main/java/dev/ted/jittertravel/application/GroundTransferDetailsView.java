package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GroundTransferId;

import java.time.LocalDateTime;

/**
 * One planned ground transfer, as the cancel confirmation page shows it: which journey is about to
 * be removed, in the same words the itinerary card uses.
 * <p>
 * Both ends arrive already written by {@link TransferEndpointLabel} — an airport code or a hotel
 * name. The page is OWNER-only, so there is nothing to redact here; redaction is an
 * anonymous-calendar concern.
 * <p>
 * Times are the transfer-zone wall-clock (both ends share one zone), which is what the traveller
 * would read off a clock at either end.
 */
public record GroundTransferDetailsView(
        GroundTransferId groundTransferId,
        String origin,
        String destination,
        LocalDateTime departsAt,
        LocalDateTime arrivesAt
) {
}
