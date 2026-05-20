package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects flight events into the "Booked Flights" list view.
 * <p>
 * Currently consumes {@link FlightBooked}. When the {@code FlightChanged}
 * event is introduced in the edit slice, this projector will also overwrite
 * the matching entry on {@code FlightChanged} (full-snapshot replace keyed
 * by {@link FlightId}).
 */
public class BookedFlightsProjector implements EventStreamConsumer {

    private static final DateTimeFormatter DEPARTURE_DISPLAY =
            DateTimeFormatter.ofPattern("M-dd-uuu h:mma", Locale.ENGLISH);

    private final Map<FlightId, BookedFlightView> viewsByFlight = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            if (storedEvent.payload() instanceof FlightBooked event) {
                viewsByFlight.put(event.flightId(), toView(event));
            }
        });
    }

    private static BookedFlightView toView(FlightBooked event) {
        String route = event.departureAirport().code()
                + "\u2192"
                + event.arrivalAirport().code();
        return new BookedFlightView(
                event.flightId(),
                event.airline(),
                event.flightNumber(),
                route,
                event.departureDateTime(),
                event.departureDateTime().format(DEPARTURE_DISPLAY)
        );
    }

    public List<BookedFlightView> views() {
        return viewsByFlight.values().stream()
                .sorted(Comparator.comparing(BookedFlightView::departureDateTime))
                .toList();
    }
}
