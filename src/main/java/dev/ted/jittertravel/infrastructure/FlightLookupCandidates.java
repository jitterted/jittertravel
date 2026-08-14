package dev.ted.jittertravel.infrastructure;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The flight segments AeroDataBox returned for one flight-number-plus-date lookup, in
 * departure order.
 * <p>
 * A flight number can cover more than one segment on a given day: the legs of a
 * direct-but-not-nonstop service (RDU-DEN-RNO), or occasionally unrelated flights that happen to
 * share the number. Nothing in the flight number and date says which one the traveller is on, so
 * this type does not guess: it reports whether a choice is needed and leaves the picking to the
 * caller. The one case it does resolve is the through-flight, offered as an extra option when the
 * segments chain end to end.
 */
public record FlightLookupCandidates(List<FlightLookupResult> segments) {

    public FlightLookupCandidates {
        segments = segments.stream()
                           .sorted(Comparator.comparing(FlightLookupResult::departureDateTime))
                           .toList();
    }

    public static FlightLookupCandidates none() {
        return new FlightLookupCandidates(List.of());
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /**
     * True when the lookup is ambiguous: more than one segment came back, and only the traveller
     * knows which leg (or the whole trip) they booked.
     */
    public boolean requiresChoice() {
        return segments.size() > 1;
    }

    public FlightLookupResult single() {
        if (segments.size() != 1) {
            throw new IllegalStateException(
                    "single() requires exactly one segment, but there are " + segments.size());
        }
        return segments.getFirst();
    }

    /**
     * The whole trip as one entry — first segment's departure, last segment's arrival — but only
     * when the segments actually chain (each arrival airport is the next departure airport).
     * Unrelated flights sharing a number never chain, so no bogus route is invented for them.
     */
    public Optional<FlightLookupResult> throughFlight() {
        if (!requiresChoice() || !segmentsChain()) {
            return Optional.empty();
        }
        FlightLookupResult first = segments.getFirst();
        FlightLookupResult last = segments.getLast();
        return Optional.of(new FlightLookupResult(
                first.airline(), first.flightNumber(),
                first.departureAirport(), first.departureDateTime(), first.departureZoneId(),
                last.arrivalAirport(), last.arrivalDateTime(), last.arrivalZoneId()));
    }

    private boolean segmentsChain() {
        for (int i = 0; i < segments.size() - 1; i++) {
            if (!segments.get(i).arrivalAirport().equals(segments.get(i + 1).departureAirport())) {
                return false;
            }
        }
        return true;
    }
}
