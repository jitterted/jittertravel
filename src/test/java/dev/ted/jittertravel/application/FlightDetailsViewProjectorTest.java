package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FlightDetailsViewProjectorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void flightBookedProducesDetailViewKeyedByFlightId() {
        FlightDetailsViewProjector projector = new FlightDetailsViewProjector();
        FlightId flightId = FlightId.random();
        ZonedTimestamp dep = zt(LocalDateTime.of(2026, 6, 6, 13, 55));
        ZonedTimestamp arr = zt(LocalDateTime.of(2026, 6, 7, 9, 45));
        FlightBooked event = new FlightBooked(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), dep,
                AirportCode.of("FRA"), arr
        );

        projector.handle(Stream.of(stored(event)));

        Optional<FlightDetailsView> view = projector.findById(flightId);
        assertThat(view).isPresent();
        assertThat(view.get().airline()).isEqualTo("United");
        assertThat(view.get().flightNumber()).isEqualTo("UA59");
        assertThat(view.get().departureAirport().code()).isEqualTo("SFO");
        assertThat(view.get().arrivalAirport().code()).isEqualTo("FRA");
        assertThat(view.get().departureDateTime()).isEqualTo(dep);
        assertThat(view.get().arrivalDateTime()).isEqualTo(arr);
    }

    @Test
    void flightChangedOverwritesPreviousDetails() {
        FlightDetailsViewProjector projector = new FlightDetailsViewProjector();
        FlightId flightId = FlightId.random();
        FlightBooked booked = new FlightBooked(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), zt(LocalDateTime.of(2026, 6, 6, 13, 55)),
                AirportCode.of("FRA"), zt(LocalDateTime.of(2026, 6, 7, 9, 45))
        );
        ZonedTimestamp newDep = zt(LocalDateTime.of(2026, 6, 8, 16, 0));
        FlightChanged changed = new FlightChanged(
                flightId, "Lufthansa", "LH441",
                AirportCode.of("SFO"), newDep,
                AirportCode.of("MUC"), zt(LocalDateTime.of(2026, 6, 9, 11, 30)),
                null
        );

        projector.handle(Stream.of(stored(booked), stored(changed)));

        FlightDetailsView view = projector.findById(flightId).orElseThrow();
        assertThat(view.airline()).isEqualTo("Lufthansa");
        assertThat(view.flightNumber()).isEqualTo("LH441");
        assertThat(view.arrivalAirport().code()).isEqualTo("MUC");
        assertThat(view.departureDateTime()).isEqualTo(newDep);
    }

    @Test
    void findByIdReturnsEmptyWhenNoFlight() {
        FlightDetailsViewProjector projector = new FlightDetailsViewProjector();
        assertThat(projector.findById(FlightId.random())).isEmpty();
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, UTC);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
