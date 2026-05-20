package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects flight events into the {@link FlightDetailsView} used by the
 * edit screen. Single-purpose: serves one view (the change-flight form).
 * <p>
 * Both {@link FlightBooked} and {@link FlightChanged} are full snapshots,
 * so each new event simply overwrites the entry keyed by {@link FlightId}.
 */
public class FlightDetailsViewProjector implements EventStreamConsumer {

    private final Map<FlightId, FlightDetailsView> viewsByFlight = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case FlightBooked event -> viewsByFlight.put(event.flightId(), new FlightDetailsView(
                        event.flightId(),
                        event.airline(),
                        event.flightNumber(),
                        event.departureAirport(),
                        event.departureDateTime(),
                        event.arrivalAirport(),
                        event.arrivalDateTime()
                ));
                case FlightChanged event -> viewsByFlight.put(event.flightId(), new FlightDetailsView(
                        event.flightId(),
                        event.airline(),
                        event.flightNumber(),
                        event.departureAirport(),
                        event.departureDateTime(),
                        event.arrivalAirport(),
                        event.arrivalDateTime()
                ));
                default -> { /* ignore non-flight events */ }
            }
        });
    }

    public Optional<FlightDetailsView> findById(FlightId flightId) {
        return Optional.ofNullable(viewsByFlight.get(flightId));
    }
}
