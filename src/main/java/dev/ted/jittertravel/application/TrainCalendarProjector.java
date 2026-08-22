package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CalendarViewBuilder;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects {@link TrainBooked} events into pre-formatted {@link CalendarEntry} views.
 * <p>
 * Same-day trips produce one entry showing both departure and arrival times.
 * Overnight trips produce two single-day entries, one on each day.
 * Multiple non-overlapping trips on the same day are each projected as separate
 * entries; the {@link CalendarViewBuilder} packs them into the same sub-row.
 */
public class TrainCalendarProjector implements EventStreamConsumer {

    private final Map<TrainTripId, List<CalendarEntry>> entriesByTrip = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case TrainBooked e -> entriesByTrip.put(e.tripId(), buildEntries(
                        e.tripId(), e.departureStation(), e.departureDateTime(),
                        e.arrivalStation(), e.arrivalDateTime(), e.serviceId()));
                case TrainChanged e -> entriesByTrip.put(e.tripId(), buildEntries(
                        e.tripId(), e.departureStation(), e.departureDateTime(),
                        e.arrivalStation(), e.arrivalDateTime(), e.serviceId()));
                default -> { /* not a train event */ }
            }
        });
    }

    private static List<CalendarEntry> buildEntries(TrainTripId tripId,
                                                    TrainStationAddress dep,
                                                    ZonedTimestamp departure,
                                                    TrainStationAddress arr,
                                                    ZonedTimestamp arrival,
                                                    String serviceId) {
        // Each end keeps its own station zone (a Frankfurt→Paris trip spans two); the day
        // column still comes from that end's local wall-clock.
        LocalDateTime depDt = departure.localDateTime();
        LocalDateTime arrDt = arrival.localDateTime();
        String route = "🚄 " + dep.city() + " → " + arr.city();
        SubtitleLine departs = new SubtitleLine.At("Departs", departure);
        SubtitleLine arrives = new SubtitleLine.At("Arrives", arrival);
        EntryDetails details = new EntryDetails.Train("/booked-trains/" + tripId.id());

        boolean sameDay = depDt.toLocalDate().equals(arrDt.toLocalDate());
        if (sameDay) {
            SubtitleLine timeRange = new SubtitleLine.Range(departure, arrival);
            List<SubtitleLine> subtitle = serviceId.isEmpty()
                    ? List.of(timeRange)
                    : List.of(new SubtitleLine.Text(serviceId), timeRange);
            return List.of(new CalendarEntry(depDt, arrDt, route, subtitle, details));
        }

        List<SubtitleLine> depSubtitle = serviceId.isEmpty()
                ? List.of(departs)
                : List.of(new SubtitleLine.Text(serviceId), departs);
        return List.of(
                new CalendarEntry(depDt, depDt, route, depSubtitle, details),
                new CalendarEntry(arrDt, arrDt, route, List.of(arrives), details)
        );
    }

    public List<CalendarEntry> entries() {
        return entriesByTrip.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
