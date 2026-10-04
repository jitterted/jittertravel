package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookFlightContext;
import dev.ted.jittertravel.domain.ChangeFlightItineraryContext;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The itinerary a confirmation code names, as the event stream says it stands (R1): which one, whether
 * it is still live, and each of its flights as last written, split into the ones still on the books
 * and the ones cancelled. Folded for one decision and thrown away.
 * <p>
 * A code can have been booked more than once (cancelled, then pasted again before pasting a cancelled
 * code was refused), so a live itinerary wins over any number of cancelled ones.
 */
final class TripOnTheBooks {

    private final FlightItineraryId itineraryId;
    private final boolean live;
    private final boolean cancelledOnly;
    private final List<ItineraryLeg> liveMembers;
    private final List<ItineraryLeg> cancelledMembers;

    private TripOnTheBooks(FlightItineraryId itineraryId, boolean live, boolean cancelledOnly,
                           List<ItineraryLeg> liveMembers, List<ItineraryLeg> cancelledMembers) {
        this.itineraryId = itineraryId;
        this.live = live;
        this.cancelledOnly = cancelledOnly;
        this.liveMembers = liveMembers;
        this.cancelledMembers = cancelledMembers;
    }

    /** An explicit loop over the stream: what a leg looks like depends on the order of its events. */
    static TripOnTheBooks fold(List<StoredEvent> events, String confirmationCode) {
        Map<FlightItineraryId, List<FlightId>> membersByItinerary = new LinkedHashMap<>();
        Set<FlightItineraryId> named = new HashSet<>();
        Set<FlightItineraryId> cancelled = new HashSet<>();
        Map<FlightId, ItineraryLeg> legs = new HashMap<>();
        Set<FlightId> cancelledFlights = new HashSet<>();
        for (StoredEvent stored : events) {
            switch (stored.payload()) {
                case FlightItineraryBooked e -> {
                    membersByItinerary.put(e.itineraryId(), e.flightIds());
                    if (e.confirmationCode().equals(confirmationCode)) {
                        named.add(e.itineraryId());
                    }
                }
                case FlightItineraryChanged e -> membersByItinerary.put(e.itineraryId(), e.flightIds());
                case FlightItineraryCancelled e -> cancelled.add(e.itineraryId());
                case FlightBooked e -> legs.put(e.flightId(), new ItineraryLeg(e.flightId(), e.airline(),
                        e.flightNumber(), e.departureAirport(), e.departureDateTime(),
                        e.arrivalAirport(), e.arrivalDateTime()));
                case FlightChanged e -> legs.put(e.flightId(), new ItineraryLeg(e.flightId(), e.airline(),
                        e.flightNumber(), e.departureAirport(), e.departureDateTime(),
                        e.arrivalAirport(), e.arrivalDateTime()));
                case FlightCancelled e -> cancelledFlights.add(e.flightId());
                default -> { /* not part of a trip */ }
            }
        }
        FlightItineraryId liveOne = named.stream().filter(id -> !cancelled.contains(id)).findFirst().orElse(null);
        if (liveOne == null) {
            return new TripOnTheBooks(null, false, !named.isEmpty(), List.of(), List.of());
        }
        List<ItineraryLeg> members = membersByItinerary.get(liveOne).stream()
                .map(legs::get)
                .filter(Objects::nonNull)
                .toList();
        return new TripOnTheBooks(liveOne, true, false,
                members.stream().filter(leg -> !cancelledFlights.contains(leg.flightId())).toList(),
                members.stream().filter(leg -> cancelledFlights.contains(leg.flightId())).toList());
    }

    boolean live() {
        return live;
    }

    /** The code names itinerary(ies), and every one of them has been cancelled. */
    boolean cancelledOnly() {
        return cancelledOnly;
    }

    FlightItineraryId itineraryId() {
        return itineraryId;
    }

    ChangeFlightItineraryContext contextFor(BookFlightContext context) {
        return new ChangeFlightItineraryContext(live, liveMembers, cancelledMembers,
                context.scheduledLegs(), context.now());
    }
}
