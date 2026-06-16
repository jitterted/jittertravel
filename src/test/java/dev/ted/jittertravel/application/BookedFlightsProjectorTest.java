package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BookedFlightsProjectorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 12, 0);

    @Test
    void flightBookedProducesViewWithRouteAndFormattedDeparture() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightId flightId = FlightId.random();
        FlightBooked event = new FlightBooked(
                flightId,
                "United Airlines",
                "UA59",
                AirportCode.of("SFO"),
                LocalDateTime.of(2026, 6, 6, 13, 55),
                AirportCode.of("FRA"),
                LocalDateTime.of(2026, 6, 7, 9, 45)
        );

        projector.handle(Stream.of(stored(event, instant(2026, 5, 20, 12, 22))));

        List<BookedFlightView> views = projector.views(TimeView.ALL, NOW);
        assertThat(views).hasSize(1);
        BookedFlightView view = views.getFirst();
        assertThat(view.flightId()).isEqualTo(flightId);
        assertThat(view.airline()).isEqualTo("United Airlines");
        assertThat(view.flightNumber()).isEqualTo("UA59");
        assertThat(view.route()).isEqualTo("SFO\u2192FRA");
        assertThat(view.departureDateTimeDisplay()).isEqualTo("Sat, Jun 6, 1:55 PM");
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

        assertThat(projector.views(TimeView.ALL, NOW))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA1", "UA2");
    }

    @Test
    void replayingTheSameEventTwiceProducesTwoHistoryEntriesForOneFlight() {
        // Replay through the projector is a one-way "events happened" stream;
        // it does not de-duplicate. The list still has one row per flight,
        // but its history reflects what was actually observed.
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked event = sampleFlight("UA1", LocalDateTime.of(2026, 6, 6, 13, 55));

        projector.handle(Stream.of(stored(event, Instant.now())));
        projector.handle(Stream.of(stored(event, Instant.now())));

        assertThat(projector.views(TimeView.ALL, NOW)).hasSize(1);
        assertThat(projector.views(TimeView.ALL, NOW).getFirst().history()).hasSize(2);
    }

    @Test
    void futureFilterExcludesFlightsDepartedBeforeNow() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked past = sampleFlight("UA1", NOW.minusDays(5));
        FlightBooked upcoming = sampleFlight("UA2", NOW.plusDays(5));

        projector.handle(Stream.of(stored(past, Instant.now()), stored(upcoming, Instant.now())));

        assertThat(projector.views(TimeView.FUTURE, NOW))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA2");
        assertThat(projector.views(TimeView.ALL, NOW))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA1", "UA2");
    }

    private static FlightBooked sampleFlight(String number, LocalDateTime departure) {
        return new FlightBooked(
                FlightId.random(),
                "United",
                number,
                AirportCode.of("SFO"),
                departure,
                AirportCode.of("LAX"),
                departure.plusHours(2)
        );
    }

    private static StoredEvent stored(Event event, Instant timestamp) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), timestamp, event, UUID.randomUUID());
    }

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }
}
