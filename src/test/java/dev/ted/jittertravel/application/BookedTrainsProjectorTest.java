package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BookedTrainsProjectorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/London");
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 6, 9, 9, 0);
    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2026, 6, 9, 13, 0);

    @Test
    void trainBookedAddsViewWithAllFields() {
        BookedTrainsProjector projector = new BookedTrainsProjector();
        TrainTripId tripId = TrainTripId.random();
        TrainBooked event = new TrainBooked(
                tripId,
                new TrainStationAddress("London Euston", "London", "UK", "https://maps.google.com/euston"),
                zt(DEPARTURE),
                new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", ""),
                zt(ARRIVAL),
                "LNER - Azuma 1A34"
        );

        projector.handle(Stream.of(stored(event)));

        List<BookedTrainView> views = projector.views(TimeView.ALL, instant(DEPARTURE));
        assertThat(views)
                .hasSize(1);
        BookedTrainView view = views.getFirst();
        assertThat(view.tripId())
                .isEqualTo(tripId);
        assertThat(view.serviceId())
                .isEqualTo("LNER - Azuma 1A34");
        assertThat(view.departureStationName())
                .isEqualTo("London Euston");
        assertThat(view.departureCity())
                .isEqualTo("London");
        assertThat(view.departureMapsUrl())
                .isEqualTo("https://maps.google.com/euston");
        assertThat(view.departureDateTime())
                .isEqualTo(zt(DEPARTURE));
        assertThat(view.arrivalStationName())
                .isEqualTo("Manchester Piccadilly");
        assertThat(view.arrivalCity())
                .isEqualTo("Manchester");
        assertThat(view.arrivalMapsUrl())
                .isEmpty();
        assertThat(view.arrivalDateTime())
                .isEqualTo(zt(ARRIVAL));
    }

    @Test
    void futureFilterExcludesTrainsDepartingBeforeNow() {
        BookedTrainsProjector projector = new BookedTrainsProjector();
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);
        TrainBooked past = trainBookedDeparting(LocalDateTime.of(2026, 6, 10, 9, 0));
        TrainBooked future = trainBookedDeparting(LocalDateTime.of(2026, 6, 20, 9, 0));

        projector.handle(Stream.of(stored(past), stored(future)));

        assertThat(projector.views(TimeView.FUTURE, instant(now)))
                .extracting(BookedTrainView::departureDateTime)
                .containsExactly(future.departureDateTime());
        assertThat(projector.views(TimeView.ALL, instant(now)))
                .extracting(BookedTrainView::departureDateTime)
                .containsExactly(past.departureDateTime(), future.departureDateTime());
    }

    @Test
    void futureFilterIncludesTrainDepartingExactlyNow() {
        BookedTrainsProjector projector = new BookedTrainsProjector();
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);
        TrainBooked departingNow = trainBookedDeparting(now);

        projector.handle(Stream.of(stored(departingNow)));

        assertThat(projector.views(TimeView.FUTURE, instant(now)))
                .hasSize(1);
    }

    @Test
    void trainChangedOverwritesTheBookedTrainViewForSameTrip() {
        BookedTrainsProjector projector = new BookedTrainsProjector();
        TrainTripId tripId = TrainTripId.random();
        TrainBooked booked = new TrainBooked(
                tripId,
                new TrainStationAddress("London Euston", "London", "UK", ""),
                zt(LocalDateTime.of(2026, 7, 1, 9, 0)),
                new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", ""),
                zt(LocalDateTime.of(2026, 7, 1, 13, 0)),
                "");
        TrainChanged changed = new TrainChanged(
                tripId,
                new TrainStationAddress("London Kings Cross", "London", "UK", ""),
                zt(LocalDateTime.of(2026, 7, 5, 10, 0)),
                new TrainStationAddress("Edinburgh Waverley", "Edinburgh", "UK", ""),
                zt(LocalDateTime.of(2026, 7, 5, 14, 30)),
                "LNER - Azuma 9E22");

        projector.handle(Stream.of(stored(booked), stored(changed)));

        List<BookedTrainView> views = projector.views(TimeView.ALL, instant(LocalDateTime.of(2026, 6, 1, 0, 0)));
        assertThat(views)
                .hasSize(1);
        BookedTrainView view = views.getFirst();
        assertThat(view.arrivalCity())
                .isEqualTo("Edinburgh");
        assertThat(view.departureDateTime())
                .isEqualTo(zt(LocalDateTime.of(2026, 7, 5, 10, 0)));
        assertThat(view.serviceId())
                .isEqualTo("LNER - Azuma 9E22");
    }

    private static TrainBooked trainBookedDeparting(LocalDateTime departure) {
        return new TrainBooked(
                TrainTripId.random(),
                new TrainStationAddress("London Euston", "London", "UK", ""),
                zt(departure),
                new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", ""),
                zt(departure.plusHours(4)),
                ""
        );
    }

    private static ZonedTimestamp zt(LocalDateTime wallClock) {
        return ZonedTimestamp.fromLocal(wallClock, ZONE);
    }

    // FUTURE/past is evaluated by instant; the trip's departure is in ZONE, so resolve "now" there.
    private static Instant instant(LocalDateTime wallClock) {
        return wallClock.atZone(ZONE).toInstant();
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
