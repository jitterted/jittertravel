package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeFlightCommandTest {

    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void emitsFlightChangedWithFullSnapshotWhenValid() {
        ChangeFlightCommand command = validCommand();

        List<FlightChanged> events = command.execute(new ChangeFlightContext(true, NOW)).toList();

        assertThat(events).hasSize(1);
        FlightChanged event = events.getFirst();
        assertThat(event.airline()).isEqualTo("United");
        assertThat(event.flightNumber()).isEqualTo("UA59");
        assertThat(event.departureAirport().code()).isEqualTo("SFO");
        assertThat(event.arrivalAirport().code()).isEqualTo("FRA");
        assertThat(event.reason()).isEmpty();
    }

    @Test
    void reasonForChangeIsCarriedOnTheEventWhenProvided() {
        ChangeFlightCommand command = new ChangeFlightCommand(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), zt(LocalDateTime.of(2026, 5, 8, 9, 0)),
                AirportCode.of("FRA"), zt(LocalDateTime.of(2026, 5, 8, 19, 0)),
                "Schedule shifted by airline");

        FlightChanged event = command.execute(new ChangeFlightContext(true, NOW)).findFirst().orElseThrow();

        assertThat(event.reason()).isEqualTo("Schedule shifted by airline");
    }

    @Test
    void rejectsWhenFlightDoesNotExist() {
        assertThatThrownBy(() -> validCommand().execute(new ChangeFlightContext(false, NOW)))
                .isInstanceOf(FlightNotFound.class);
    }

    @Test
    void rejectsWhenDepartureIsNotInFuture() {
        ChangeFlightCommand command = new ChangeFlightCommand(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), zt(LocalDateTime.of(2026, 4, 30, 9, 0)),
                AirportCode.of("FRA"), zt(LocalDateTime.of(2026, 5, 8, 19, 0)),
                null);

        assertThatThrownBy(() -> command.execute(new ChangeFlightContext(true, NOW)))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void rejectsWhenArrivalIsNotAfterDeparture() {
        ZonedTimestamp dep = zt(LocalDateTime.of(2026, 5, 8, 9, 0));
        ChangeFlightCommand command = new ChangeFlightCommand(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), dep,
                AirportCode.of("FRA"), dep,
                null);

        assertThatThrownBy(() -> command.execute(new ChangeFlightContext(true, NOW)))
                .isInstanceOf(InvalidDateRange.class);
    }

    private ChangeFlightCommand validCommand() {
        return new ChangeFlightCommand(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), zt(LocalDateTime.of(2026, 5, 8, 9, 0)),
                AirportCode.of("FRA"), zt(LocalDateTime.of(2026, 5, 8, 19, 0)),
                null);
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, UTC);
    }
}
