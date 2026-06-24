package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects {@link FlightBooked} events into pre-formatted {@link CalendarEntry}
 * views ready for the calendar swimlane renderer.
 * <p>
 * Rendering rules:
 * <ul>
 *   <li>Same-day flight: a single entry with the route as the title and a
 *       two-line subtitle (departure on the first line, arrival on the second).</li>
 *   <li>Multi-day flight: two entries — one on the departure day showing the
 *       departure time, and one on the arrival day showing the arrival time.
 *       Both carry the same route title.</li>
 * </ul>
 */
public class FlightCalendarProjector implements EventStreamConsumer {

    private static final DateTimeFormatter TIME_OF_DAY =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final Map<FlightId, List<CalendarEntry>> entriesByFlight = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case FlightBooked event -> entriesByFlight.put(event.flightId(), buildEntries(
                        event.flightId(), event.departureAirport(), event.arrivalAirport(),
                        event.departureDateTime(), event.arrivalDateTime()));
                case FlightChanged event -> entriesByFlight.put(event.flightId(), buildEntries(
                        event.flightId(), event.departureAirport(), event.arrivalAirport(),
                        event.departureDateTime(), event.arrivalDateTime()));
                default -> { /* not a flight event */ }
            }
        });
    }

    private static List<CalendarEntry> buildEntries(FlightId flightId,
                                                    AirportCode departureAirport,
                                                    AirportCode arrivalAirport,
                                                    ZonedTimestamp departureDateTime,
                                                    ZonedTimestamp arrivalDateTime) {
        LocalDateTime depLocal = departureDateTime.localDateTime();
        LocalDateTime arrLocal = arrivalDateTime.localDateTime();
        String route = "✈️ " + departureAirport.code() + "→" + arrivalAirport.code();
        String departs = "Departs " + depLocal.format(TIME_OF_DAY);
        String arrives = "Arrives " + arrLocal.format(TIME_OF_DAY);
        String editPath = "/booked-flights/" + flightId.id();

        boolean sameDay = depLocal.toLocalDate().equals(arrLocal.toLocalDate());

        if (sameDay) {
            String timeRange = depLocal.format(TIME_OF_DAY) + " → " + arrLocal.format(TIME_OF_DAY);
            return List.of(new CalendarEntry(
                    EntryKind.FLIGHT,
                    depLocal,
                    arrLocal,
                    route,
                    List.of(timeRange),
                    null,
                    null,
                    null,
                    editPath
            ));
        }

        CalendarEntry departureEntry = new CalendarEntry(
                EntryKind.FLIGHT,
                depLocal,
                depLocal,
                route,
                List.of(departs),
                null,
                null,
                null,
                editPath
        );
        CalendarEntry arrivalEntry = new CalendarEntry(
                EntryKind.FLIGHT,
                arrLocal,
                arrLocal,
                route,
                List.of(arrives),
                null,
                null,
                null,
                editPath
        );
        return List.of(departureEntry, arrivalEntry);
    }

    public List<CalendarEntry> entries() {
        return entriesByFlight.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
