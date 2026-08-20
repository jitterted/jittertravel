package dev.ted.jittertravel.domain;

/**
 * A short hop with no booking — the taxi from the airport to the hotel, the subway back — has been
 * planned. It exists to fill a missing-travel gap: it enters the schedule timeline as a movement,
 * exactly as a flight or a train leg does. See {@code docs/GroundTransferPlan.md}.
 * <p>
 * <strong>Flat, not a sealed {@code TransferPoint} hierarchy.</strong> A sealed
 * {@code AirportPoint | PlacePoint} reads better, but a polymorphic record inside an event payload
 * needs Jackson type information in the stored JSON, and every stored payload is a compatibility
 * commitment. Two {@code String}s and an {@link Address} per end cost nothing at rest.
 * <p>
 * The privacy split is per field, and the redactor's rule is simple: <em>if the airport code is
 * non-blank, publish the code; otherwise publish city / region / country.</em> The
 * {@code originName}/{@code destinationName} (a hotel name) and the street are <strong>never</strong>
 * published — see the redaction rules in CLAUDE.md.
 * <p>
 * Both {@link Address}es are <strong>snapshots</strong>, copied at submit time: changing the hotel
 * later must not silently rewrite a transfer already recorded.
 */
public record GroundTransferPlanned(
        GroundTransferId groundTransferId,
        String originAirportCode,
        String originName,
        Address origin,
        String destinationAirportCode,
        String destinationName,
        Address destination,
        ZonedTimestamp departsAt,
        ZonedTimestamp arrivesAt
) implements Event {
    public GroundTransferPlanned {
        originAirportCode = blankWhenNull(originAirportCode);
        originName = blankWhenNull(originName);
        destinationAirportCode = blankWhenNull(destinationAirportCode);
        destinationName = blankWhenNull(destinationName);
    }

    private static String blankWhenNull(String value) {
        return value == null ? "" : value;
    }
}
