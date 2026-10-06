package dev.ted.jittertravel.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @param originMoment      the origin's own moment (a landing, an arrival, a check-out), or
 *                          {@code null} when the token named a place with none, such as a bare
 *                          {@code airport:DEN}. Never stored: it exists only so the date rule has
 *                          something to compare against, and {@link GroundTransferPlanned} does not
 *                          carry it.
 * @param destinationMoment the destination's own moment (a departure, a check-in), or {@code null}
 */
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
        String mode,
        ZonedTimestamp originMoment,
        ZonedTimestamp destinationMoment
) implements DomainCommand<PlanGroundTransferContext> {

    /** The most the two endpoints' own moments may differ by — an overnight hop is still one hop. */
    private static final Duration ENDPOINTS_APART_AT_MOST = Duration.ofHours(24);

    /** A transfer whose endpoints' moments are not known, so the date rule has nothing to say. */
    public PlanGroundTransferCommand(
            GroundTransferId groundTransferId,
            String originAirportCode, String originName, Address origin,
            String destinationAirportCode, String destinationName, Address destination,
            ZonedTimestamp departsAt, ZonedTimestamp arrivesAt, String mode) {
        this(groundTransferId, originAirportCode, originName, origin,
                destinationAirportCode, destinationName, destination,
                departsAt, arrivesAt, mode, null, null);
    }

    @Override
    public Stream<GroundTransferPlanned> execute(PlanGroundTransferContext context) {
        // No future-date check (D6): a transfer is normally entered mid-trip, for a day that has
        // already started or even already passed, to close a gap the trip has already raised.
        // Copying the gathering/private-event date rule here would break exactly the case the
        // feature exists for. The rules below constrain the date *relative to the endpoints*, never
        // relative to now.
        if (departsAt == null || arrivesAt == null || !arrivesAt.utc().isAfter(departsAt.utc())) {
            throw new InvalidGroundTransferTimeRange("Arrival time must be after departure time");
        }
        requireDateToFitTheEndpoints();
        return Stream.of(new GroundTransferPlanned(
                groundTransferId,
                originAirportCode, originName, origin,
                destinationAirportCode, destinationName, destination,
                departsAt, arrivesAt, mode));
    }

    /**
     * Both halves apply only to the moments that are known: with two, they must be within a day of
     * each other; with at least one, the typed date must be that moment's day in its own zone. A
     * hotel's moment is a guess (check-out leaving, check-in arriving), so a mid-stay ride from a
     * hotel is refused too — accepted deliberately (Ted, 2026-10-06) until gatherings and private
     * events can be picked as endpoints.
     */
    private void requireDateToFitTheEndpoints() {
        var known = Stream.of(originMoment, destinationMoment)
                .filter(Objects::nonNull)
                .toList();
        if (known.size() == 2 && Duration.between(known.get(0).utc(), known.get(1).utc()).abs()
                .compareTo(ENDPOINTS_APART_AT_MOST) > 0) {
            throw new InvalidGroundTransferDate("Date must be within a day of both places");
        }
        var typedDate = departsAt.atEntryZone().toLocalDate();
        if (!known.isEmpty() && known.stream()
                .noneMatch(moment -> moment.atEntryZone().toLocalDate().equals(typedDate))) {
            throw new InvalidGroundTransferDate("Date must be within a day of both places");
        }
    }
}
