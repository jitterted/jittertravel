package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public record PlanGroundTransferCommand(
        GroundTransferId groundTransferId,
        String originAirportCode,
        String originName,
        Address origin,
        String destinationAirportCode,
        String destinationName,
        Address destination,
        ZonedTimestamp departsAt,
        ZonedTimestamp arrivesAt,
        String mode
) implements DomainCommand<PlanGroundTransferContext> {

    @Override
    public Stream<GroundTransferPlanned> execute(PlanGroundTransferContext context) {
        // The ONLY rule. Deliberately no future-date check (D6): a transfer is normally entered
        // mid-trip, for a day that has already started or even already passed, to close a gap the
        // trip has already raised. Copying the gathering/private-event date rule here would break
        // exactly the case the feature exists for.
        if (departsAt == null || arrivesAt == null || !arrivesAt.utc().isAfter(departsAt.utc())) {
            throw new InvalidGroundTransferTimeRange("Arrival time must be after departure time");
        }
        return Stream.of(new GroundTransferPlanned(
                groundTransferId,
                originAirportCode, originName, origin,
                destinationAirportCode, destinationName, destination,
                departsAt, arrivesAt, mode));
    }
}
