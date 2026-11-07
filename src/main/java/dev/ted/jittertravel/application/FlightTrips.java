package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.Journey;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Composes the flights list with the itineraries, a layer above both (R12: a read model is built
 * from events alone, and these two are each built from their own).
 * <p>
 * Membership is by flight id, never by dates. An itinerary booked inside the gap of another is two
 * itineraries whose legs interleave in the list, and neither's cancellation can touch the other's
 * flights.
 */
public class FlightTrips {

    private final BookedFlightsProjector flights;
    private final BookedItinerariesProjector itineraries;

    public FlightTrips(BookedFlightsProjector flights, BookedItinerariesProjector itineraries) {
        this.flights = flights;
        this.itineraries = itineraries;
    }

    /**
     * The trip each listed flight belongs to; a flight entered by hand has no entry. Each itinerary
     * is summarised once, from all of its legs whatever the filter hides, and every listed leg of it
     * shares that one summary. Hues go out in order of first appearance down the list, alternating.
     */
    public Map<FlightId, FlightTrip> forList(List<BookedFlightView> listed, Instant now) {
        Map<FlightId, FlightTrip> trips = new HashMap<>();
        Map<FlightItineraryId, ItinerarySummary> summaries = new HashMap<>();
        Map<FlightItineraryId, List<FlightId>> flyingLegs = new HashMap<>();
        for (BookedFlightView view : listed) {
            Optional<BookedItineraryView> itinerary = itineraries.findContaining(view.flightId());
            if (itinerary.isEmpty()) {
                continue;
            }
            FlightItineraryId id = itinerary.get().itineraryId();
            if (!summaries.containsKey(id)) {
                List<BookedFlightView> members = membersOf(itinerary.get());
                flyingLegs.put(id, members.stream()
                        .filter(member -> !member.cancelled())
                        .map(BookedFlightView::flightId)
                        .toList());
                summaries.put(id, summaryOf(itinerary.get(), members, now)
                        .withHue(summaries.size() % ItinerarySummary.HUES));
            }
            trips.put(view.flightId(),
                    new FlightTrip(summaries.get(id), flyingLegs.get(id).indexOf(view.flightId()) + 1));
        }
        return trips;
    }

    /**
     * Live flights the itinerary's cancellation leaves alone although they fall between its first
     * and last live flight, in departure order. Empty when there is nothing live to cancel.
     */
    public List<StayingFlight> staysBooked(BookedItineraryView itinerary, Instant now) {
        List<Instant> departures = itinerary.flightIds().stream()
                .map(flightId -> memberOf(itinerary, flightId))
                .filter(view -> !view.cancelled())
                .map(view -> view.departureDateTime().utc())
                .sorted()
                .toList();
        if (departures.isEmpty()) {
            return List.of();
        }
        Instant first = departures.getFirst();
        Instant last = departures.getLast();
        return flights.views(TimeView.ALL, CancelledView.HIDE, now).stream()
                .filter(view -> !itinerary.flightIds().contains(view.flightId()))
                .filter(view -> view.departureDateTime().utc().isAfter(first)
                                && view.departureDateTime().utc().isBefore(last))
                .map(view -> new StayingFlight(view, itineraries.findContaining(view.flightId())
                        .map(BookedItineraryView::confirmationCode)
                        .orElse("")))
                .toList();
    }

    /** The itinerary's legs in departure order, cancelled ones included. */
    private List<BookedFlightView> membersOf(BookedItineraryView itinerary) {
        return itinerary.flightIds().stream()
                .map(flightId -> memberOf(itinerary, flightId))
                .sorted(Comparator.comparing(view -> view.departureDateTime().utc()))
                .toList();
    }

    /**
     * An itinerary's legs are written in the same append as the itinerary event that names them, so
     * a member the flights read model does not know is a corrupt log, not a leg to leave out: left
     * out, the list would number and count a trip's legs wrongly and say nothing.
     */
    private BookedFlightView memberOf(BookedItineraryView itinerary, FlightId flightId) {
        return flights.find(flightId)
                .orElseThrow(() -> new IllegalStateException("Itinerary " + itinerary.itineraryId().id()
                                                             + " names flight " + flightId.id()
                                                             + ", which was never booked"));
    }

    private ItinerarySummary summaryOf(BookedItineraryView itinerary, List<BookedFlightView> members,
                                       Instant now) {
        List<BookedFlightView> flying = members.stream()
                .filter(view -> !view.cancelled())
                .toList();
        List<BookedFlightView> described = flying.isEmpty() ? members : flying;
        int departed = (int) flying.stream()
                .filter(view -> view.departureDateTime().hasPassed(now))
                .count();
        return new ItinerarySummary(itinerary.itineraryId(), itinerary.confirmationCode(),
                Journey.of(described.stream().map(BookedFlightView::asLeg).toList()),
                described.size(), departed, !flying.isEmpty(), itinerary.cancelled(), 0);
    }
}
