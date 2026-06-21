package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects flight events into the "Booked Flights" list view.
 * <p>
 * Both {@link FlightBooked} and {@link FlightChanged} are full snapshots,
 * so the latest snapshot wins for the row fields. The complete chronological
 * history of events is preserved and exposed on the view so the template
 * can render an inline expandable change-history beneath each row.
 */
public class BookedFlightsProjector implements EventStreamConsumer {

    private static final DateTimeFormatter DATETIME_DISPLAY =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter TIMESTAMP_DISPLAY =
            DateTimeFormatter.ofPattern("uuuu-MM-dd h:mma", Locale.ENGLISH);

    private final Map<FlightId, BookedFlightView> viewsByFlight = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case FlightBooked event -> apply(
                        event.flightId(), event.airline(), event.flightNumber(),
                        event.departureAirport(), event.arrivalAirport(),
                        event.departureDateTime(), event.arrivalDateTime(),
                        bookingEntry(storedEvent.timestamp()));
                case FlightChanged event -> apply(
                        event.flightId(), event.airline(), event.flightNumber(),
                        event.departureAirport(), event.arrivalAirport(),
                        event.departureDateTime(), event.arrivalDateTime(),
                        changeEntry(storedEvent.timestamp(), event.reason()));
                default -> { /* not a flight event */ }
            }
        });
    }

    private void apply(FlightId flightId,
                       String airline,
                       String flightNumber,
                       AirportCode departureAirport,
                       AirportCode arrivalAirport,
                       LocalDateTime departureDateTime,
                       LocalDateTime arrivalDateTime,
                       ChangeEntry newEntry) {
        viewsByFlight.compute(flightId, (id, previous) -> {
            List<ChangeEntry> history = previous == null
                    ? List.of(newEntry)
                    : appendHistory(previous.history(), newEntry);
            String route = departureAirport.code() + "\u2192" + arrivalAirport.code();
            return new BookedFlightView(
                    flightId,
                    airline,
                    flightNumber,
                    route,
                    departureDateTime,
                    departureDateTime.format(DATETIME_DISPLAY),
                    arrivalDateTime.format(DATETIME_DISPLAY),
                    history
            );
        });
    }

    private static List<ChangeEntry> appendHistory(List<ChangeEntry> previous, ChangeEntry next) {
        List<ChangeEntry> appended = new ArrayList<>(previous.size() + 1);
        appended.addAll(previous);
        appended.add(next);
        return List.copyOf(appended);
    }

    private static ChangeEntry bookingEntry(Instant timestamp) {
        LocalDateTime ts = toLocal(timestamp);
        return new ChangeEntry(ts, "Booked on " + ts.format(TIMESTAMP_DISPLAY));
    }

    private static ChangeEntry changeEntry(Instant timestamp, String reason) {
        LocalDateTime ts = toLocal(timestamp);
        String formatted = ts.format(TIMESTAMP_DISPLAY);
        String text = (reason == null || reason.isBlank())
                ? "Changed on " + formatted
                : reason + " (changed on " + formatted + ")";
        return new ChangeEntry(ts, text);
    }

    private static LocalDateTime toLocal(Instant timestamp) {
        return timestamp.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public List<BookedFlightView> views(TimeView timeView, LocalDateTime now) {
        return viewsByFlight.values().stream()
                .filter(view -> timeView.includes(view, now))
                .sorted(Comparator.comparing(BookedFlightView::departureDateTime))
                .toList();
    }
}
