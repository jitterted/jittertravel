package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CalendarViewBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    private static final DateTimeFormatter TIME_OF_DAY =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final Map<TrainTripId, List<CalendarEntry>> entriesByTrip = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            if (stored.payload() instanceof TrainBooked event) {
                entriesByTrip.put(event.tripId(), buildEntries(
                        event.departureStation(), event.departureDateTime(),
                        event.arrivalStation(), event.arrivalDateTime(),
                        event.serviceId()));
            }
        });
    }

    private static List<CalendarEntry> buildEntries(TrainStationAddress dep,
                                                    LocalDateTime depDt,
                                                    TrainStationAddress arr,
                                                    LocalDateTime arrDt,
                                                    String serviceId) {
        String route = "🚄 " + dep.city() + " → " + arr.city();
        String departs = "Departs " + depDt.format(TIME_OF_DAY);
        String arrives = "Arrives " + arrDt.format(TIME_OF_DAY);

        boolean sameDay = depDt.toLocalDate().equals(arrDt.toLocalDate());
        if (sameDay) {
            String timeRange = depDt.format(TIME_OF_DAY) + " → " + arrDt.format(TIME_OF_DAY);
            List<String> subtitle = serviceId.isEmpty()
                    ? List.of(timeRange)
                    : List.of(serviceId, timeRange);
            return List.of(new CalendarEntry(
                    EntryKind.TRAIN, depDt, arrDt,
                    route, subtitle,
                    null, null, null));
        }

        List<String> depSubtitle = serviceId.isEmpty()
                ? List.of(departs)
                : List.of(serviceId, departs);
        return List.of(
                new CalendarEntry(EntryKind.TRAIN, depDt, depDt, route, depSubtitle, null, null, null),
                new CalendarEntry(EntryKind.TRAIN, arrDt, arrDt, route, List.of(arrives), null, null, null)
        );
    }

    public List<CalendarEntry> entries() {
        return entriesByTrip.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
