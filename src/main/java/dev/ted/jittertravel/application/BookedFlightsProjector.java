package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
                // Kept as a record, not removed: the one flight read model that does, so a
                // cancelled flight can be looked up behind ?cancelled=show (see FlightCancelled).
                case FlightCancelled event -> viewsByFlight.computeIfPresent(event.flightId(),
                        (id, view) -> view.cancelledWith(event.reason(), event.cancelledOn()));
                default -> { /* not a flight event */ }
            }
        });
    }

    private void apply(FlightId flightId,
                       String airline,
                       String flightNumber,
                       AirportCode departureAirport,
                       AirportCode arrivalAirport,
                       ZonedTimestamp departureDateTime,
                       ZonedTimestamp arrivalDateTime,
                       ChangeEntry newEntry) {
        viewsByFlight.compute(flightId, (id, previous) -> {
            List<ChangeEntry> history = previous == null
                    ? List.of(newEntry)
                    : appendHistory(previous.history(), newEntry);
            String route = departureAirport.code() + "→" + arrivalAirport.code();
            return new BookedFlightView(
                    flightId,
                    airline,
                    flightNumber,
                    route,
                    departureDateTime,
                    arrivalDateTime,
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
        return timestamp.atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    /** The default list: live flights only. */
    public List<BookedFlightView> views(TimeView timeView, Instant now) {
        return views(timeView, CancelledView.HIDE, now);
    }

    public List<BookedFlightView> views(TimeView timeView, CancelledView cancelledView, Instant now) {
        return viewsByFlight.values().stream()
                .filter(view -> timeView.includes(view, now))
                .filter(cancelledView::includes)
                .sorted(Comparator.comparing(v -> v.departureDateTime().utc()))
                .toList();
    }

    /**
     * How many cancelled flights the time filter admits — what the list's switch counts, needed
     * even while they are hidden, since the switch reports what the page is leaving out.
     */
    public int cancelledCount(TimeView timeView, Instant now) {
        return (int) viewsByFlight.values().stream()
                .filter(view -> timeView.includes(view, now))
                .filter(BookedFlightView::cancelled)
                .count();
    }
}
