package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BookedFlightsProjectorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Instant NOW_INSTANT = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void flightBookedProducesViewWithRouteAndDeparture() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightId flightId = FlightId.random();
        ZonedTimestamp dep = zt(LocalDateTime.of(2026, 6, 6, 13, 55));
        ZonedTimestamp arr = zt(LocalDateTime.of(2026, 6, 7, 9, 45));
        FlightBooked event = new FlightBooked(
                flightId,
                "United Airlines",
                "UA59",
                AirportCode.of("SFO"),
                dep,
                AirportCode.of("FRA"),
                arr
        );

        projector.handle(Stream.of(stored(event, instant(2026, 5, 20, 12, 22))));

        List<BookedFlightView> views = projector.views(TimeView.ALL, NOW_INSTANT);
        assertThat(views).hasSize(1);
        BookedFlightView view = views.getFirst();
        assertThat(view.flightId()).isEqualTo(flightId);
        assertThat(view.airline()).isEqualTo("United Airlines");
        assertThat(view.flightNumber()).isEqualTo("UA59");
        assertThat(view.route()).isEqualTo("SFO→FRA");
        assertThat(view.departureDateTime()).isEqualTo(dep);
        assertThat(view.arrivalDateTime()).isEqualTo(arr);
        assertThat(view.hasChanges()).isFalse();
        assertThat(view.history())
                .extracting(ChangeEntry::displayText)
                .containsExactly("Booked on 2026-05-20 12:22PM");
    }

    @Test
    void viewsAreSortedByDepartureDateTimeAscending() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked later = sampleFlight("UA2", LocalDateTime.of(2026, 7, 1, 9, 0));
        FlightBooked earlier = sampleFlight("UA1", LocalDateTime.of(2026, 6, 1, 9, 0));

        projector.handle(Stream.of(stored(later, Instant.now()), stored(earlier, Instant.now())));

        assertThat(projector.views(TimeView.ALL, NOW_INSTANT))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA1", "UA2");
    }

    @Test
    void replayingTheSameEventTwiceProducesTwoHistoryEntriesForOneFlight() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked event = sampleFlight("UA1", LocalDateTime.of(2026, 6, 6, 13, 55));

        projector.handle(Stream.of(stored(event, Instant.now())));
        projector.handle(Stream.of(stored(event, Instant.now())));

        assertThat(projector.views(TimeView.ALL, NOW_INSTANT)).hasSize(1);
        assertThat(projector.views(TimeView.ALL, NOW_INSTANT).getFirst().history()).hasSize(2);
    }

    @Test
    void futureFilterExcludesFlightsDepartedBeforeNow() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked past = sampleFlight("UA1", LocalDateTime.of(2026, 6, 10, 12, 0));
        FlightBooked upcoming = sampleFlight("UA2", LocalDateTime.of(2026, 6, 20, 12, 0));

        projector.handle(Stream.of(stored(past, Instant.now()), stored(upcoming, Instant.now())));

        assertThat(projector.views(TimeView.FUTURE, NOW_INSTANT))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA2");
        assertThat(projector.views(TimeView.ALL, NOW_INSTANT))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA1", "UA2");
    }

    private static FlightBooked sampleFlight(String number, LocalDateTime departure) {
        return new FlightBooked(
                FlightId.random(),
                "United",
                number,
                AirportCode.of("SFO"),
                zt(departure),
                AirportCode.of("LAX"),
                zt(departure.plusHours(2))
        );
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, UTC);
    }

    private static StoredEvent stored(Event event, Instant timestamp) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), timestamp, event, UUID.randomUUID());
    }

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atOffset(ZoneOffset.UTC)
                .toInstant();
    }
}
