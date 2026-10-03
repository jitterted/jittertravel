package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Cancels every live leg of an itinerary, and the itinerary, in one append.
 * <p>
 * <strong>Refused once any live leg has departed</strong> (Ted, 2026-09-30): before the first
 * departure the trip cancels whole, afterwards the legs are cancelled one at a time with
 * {@link CancelFlightCommand}. A cancelled itinerary that left a flown leg standing, or erased one
 * that was flown, would each be a lie about what happened.
 * <p>
 * Emits one {@link FlightCancelled} per leg still live — in the legs' own order, carrying the same
 * reason and time — then {@link FlightItineraryCancelled}. {@code reason} is carried and never
 * inspected.
 */
public record CancelFlightItineraryCommand(
        FlightItineraryId itineraryId,
        String reason,
        Instant cancelledOn
) implements DomainCommand<CancelFlightItineraryContext> {

    @Override
    public Stream<Event> execute(CancelFlightItineraryContext context) {
        if (!context.itineraryLive()) {
            throw new FlightItineraryNotFound("No flight itinerary found to cancel: " + itineraryId);
        }
        boolean someLegDeparted = context.liveLegs().stream()
                .anyMatch(leg -> leg.departure().hasPassed(context.now()));
        if (someLegDeparted) {
            throw new FlightItineraryHasDeparted(
                    "A leg of this itinerary has already departed: " + itineraryId);
        }
        return Stream.concat(
                context.liveLegs().stream()
                        .map(ScheduledLeg::id)
                        .flatMap(id -> id instanceof ScheduledLegId.Flight flight
                                ? Stream.of(flight.id()) : Stream.<FlightId>empty())
                        .map(flightId -> (Event) new FlightCancelled(flightId, reason, cancelledOn)),
                Stream.of(new FlightItineraryCancelled(itineraryId, reason, cancelledOn)));
    }
}
