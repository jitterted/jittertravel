package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Command record for cancelling a planned ground transfer. A record rather than a form bean: the id
 * is the whole of the input and it comes from the path, so the controller builds this directly
 * (mirrors {@link CancelHotelRequest}, minus its reason — a transfer has no booking to explain
 * away).
 */
public record CancelGroundTransferRequest(
        UUID groundTransferId
) {
}
