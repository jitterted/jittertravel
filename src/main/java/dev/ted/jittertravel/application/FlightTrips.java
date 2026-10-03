package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;

import java.time.Instant;
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
     * The trip each listed flight belongs to; a flight entered by hand has no entry. Hues go out in
     * order of first appearance down the list, alternating.
     */
    public Map<FlightId, FlightTrip> forList(List<BookedFlightView> listed, Instant now) {
        Map<FlightId, FlightTrip> trips = new HashMap<>();
        Map<FlightItineraryId, Integer> hueByItinerary = new HashMap<>();
        for (BookedFlightView view : listed) {
            Optional<BookedItineraryView> itinerary = itineraries.findContaining(view.flightId());
            if (itinerary.isEmpty()) {
                continue;
            }
            FlightItineraryId id = itinerary.get().itineraryId();
            if (!hueByItinerary.containsKey(id)) {
                hueByItinerary.put(id, hueByItinerary.size() % FlightTrip.HUES);
            }
            trips.put(view.flightId(),
                    tripOf(itinerary.get(), view.flightId(), now).withHue(hueByItinerary.get(id)));
        }
        return trips;
    }

    /**
     * Live flights the itinerary's cancellation leaves alone although they fall between its first
     * and last live flight, in departure order. Empty when there is nothing live to cancel.
     */
    public List<StayingFlight> staysBooked(BookedItineraryView itinerary, Instant now) {
        List<Instant> departures = itinerary.flightIds().stream()
                .map(flights::find)
                .flatMap(Optional::stream)
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

    private FlightTrip tripOf(BookedItineraryView itinerary, FlightId flightId, Instant now) {
        int departed = (int) itinerary.flightIds().stream()
                .map(flights::find)
                .flatMap(Optional::stream)
                .filter(view -> !view.cancelled())
                .filter(view -> view.departureDateTime().hasPassed(now))
                .count();
        return new FlightTrip(itinerary.itineraryId(), itinerary.confirmationCode(),
                itinerary.flightIds().indexOf(flightId) + 1, itinerary.flightIds().size(), departed, 0);
    }
}
