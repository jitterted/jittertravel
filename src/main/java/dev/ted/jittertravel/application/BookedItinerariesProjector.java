package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects the itinerary events into {@link BookedItineraryView}s, for the surfaces that act on a
 * trip as one unit. Built from the two itinerary events alone: which flights a view's legs are, and
 * whether each is still on the books, belong to the flight read models and are composed a layer up.
 */
public class BookedItinerariesProjector implements EventStreamConsumer {

    private final Map<FlightItineraryId, BookedItineraryView> viewsByItinerary = new ConcurrentHashMap<>();
    private final Map<FlightId, FlightItineraryId> itineraryByFlight = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case FlightItineraryBooked event -> {
                    viewsByItinerary.put(event.itineraryId(),
                            new BookedItineraryView(event.itineraryId(), event.airline(),
                                    event.confirmationCode(), event.flightIds(), false));
                    event.flightIds().forEach(flightId -> itineraryByFlight.put(flightId, event.itineraryId()));
                }
                case FlightItineraryChanged event -> {
                    viewsByItinerary.computeIfPresent(event.itineraryId(),
                            (id, view) -> view.withFlights(event.flightIds()));
                    event.flightIds().forEach(flightId -> itineraryByFlight.put(flightId, event.itineraryId()));
                }
                case FlightItineraryCancelled event -> viewsByItinerary.computeIfPresent(event.itineraryId(),
                        (id, view) -> view.cancelledNow());
                default -> { /* not an itinerary event */ }
            }
        });
    }

    /**
     * The itinerary a flight was booked with, cancelled or not: a cancelled flight still belongs to
     * the trip it was part of, and the list shows it so.
     */
    public Optional<BookedItineraryView> findContaining(FlightId flightId) {
        return Optional.ofNullable(itineraryByFlight.get(flightId))
                .map(viewsByItinerary::get);
    }

    /** The itinerary if it exists and has not been cancelled. */
    public Optional<BookedItineraryView> findLive(FlightItineraryId itineraryId) {
        return Optional.ofNullable(viewsByItinerary.get(itineraryId))
                .filter(view -> !view.cancelled());
    }
}
