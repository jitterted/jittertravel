package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BookedFlightsProjectorFlightChangedTest {

    @Test
    void flightChangedOverwritesPreviousListEntry() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightId flightId = FlightId.random();
        FlightBooked booked = new FlightBooked(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 6, 6, 13, 55),
                AirportCode.of("FRA"), LocalDateTime.of(2026, 6, 7, 9, 45)
        );
        FlightChanged changed = new FlightChanged(
                flightId, "Lufthansa", "LH441",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 6, 8, 16, 0),
                AirportCode.of("MUC"), LocalDateTime.of(2026, 6, 9, 11, 30),
                null
        );

        projector.handle(Stream.of(stored(booked, Instant.now()), stored(changed, Instant.now())));

        List<BookedFlightView> views = projector.views();
        assertThat(views).hasSize(1);
        BookedFlightView view = views.getFirst();
        assertThat(view.airline()).isEqualTo("Lufthansa");
        assertThat(view.flightNumber()).isEqualTo("LH441");
        assertThat(view.route()).isEqualTo("SFO\u2192MUC");
    }

    @Test
    void historyIncludesBookingFirstThenEachChangeInChronologicalOrder() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightId flightId = FlightId.random();
        Instant bookingTs = instant(2026, 5, 20, 12, 22);
        Instant change1Ts = instant(2026, 6, 1, 9, 5);
        Instant change2Ts = instant(2026, 6, 15, 16, 30);

        FlightBooked booked = sampleBooked(flightId);
        FlightChanged firstChange = sampleChanged(flightId, "Wanted to fly home earlier");
        FlightChanged secondChange = sampleChanged(flightId, null);

        projector.handle(Stream.of(
                stored(booked, bookingTs),
                stored(firstChange, change1Ts),
                stored(secondChange, change2Ts)
        ));

        BookedFlightView view = projector.views().getFirst();
        assertThat(view.hasChanges()).isTrue();
        assertThat(view.history())
                .extracting(ChangeEntry::displayText)
                .containsExactly(
                        "Booked on 2026-05-20 12:22PM",
                        "Wanted to fly home earlier (changed on 2026-06-01 9:05AM)",
                        "Changed on 2026-06-15 4:30PM"
                );
        assertThat(view.latestChangeDisplay()).isEqualTo("Changed on 2026-06-15 4:30PM");
    }

    @Test
    void flightBookedAloneHasNoChanges() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked booked = sampleBooked(FlightId.random());

        projector.handle(Stream.of(stored(booked, Instant.now())));

        BookedFlightView view = projector.views().getFirst();
        assertThat(view.hasChanges()).isFalse();
        assertThat(view.history()).hasSize(1);
        assertThat(view.history().getFirst().displayText()).startsWith("Booked on ");
    }

    private static FlightBooked sampleBooked(FlightId flightId) {
        return new FlightBooked(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 6, 6, 13, 55),
                AirportCode.of("FRA"), LocalDateTime.of(2026, 6, 7, 9, 45)
        );
    }

    private static FlightChanged sampleChanged(FlightId flightId, String reason) {
        return new FlightChanged(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 6, 6, 13, 55),
                AirportCode.of("FRA"), LocalDateTime.of(2026, 6, 7, 9, 45),
                reason
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
