package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
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
        String editPath = "/booked-flights/" + flightId.id();

        // Each endpoint keeps its own airport zone; the renderer formats and can re-localize it.
        boolean sameDay = depLocal.toLocalDate().equals(arrLocal.toLocalDate());

        if (sameDay) {
            return List.of(new CalendarEntry(
                    EntryKind.FLIGHT,
                    depLocal,
                    arrLocal,
                    route,
                    List.of(new SubtitleLine.Range(departureDateTime, arrivalDateTime)),
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
                List.of(new SubtitleLine.At("Departs", departureDateTime)),
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
                List.of(new SubtitleLine.At("Arrives", arrivalDateTime)),
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
