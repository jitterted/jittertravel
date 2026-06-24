package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookFlightCommandTest {

    private static final Instant NOW = Instant.parse("2026-05-16T10:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void executeProducesFlightBookedEvent() {
        ZonedTimestamp departure = zt(LocalDateTime.of(2026, 5, 17, 9, 0));
        ZonedTimestamp arrival = zt(LocalDateTime.of(2026, 5, 17, 14, 0));
        FlightId flightId = FlightId.random();
        BookFlightCommand command = new BookFlightCommand(
                flightId, "United", "UA100",
                AirportCode.of("SFO"), departure,
                AirportCode.of("JFK"), arrival);

        List<FlightBooked> events = command.execute(new BookFlightContext(NOW)).toList();

        assertThat(events).hasSize(1);
        FlightBooked event = events.getFirst();
        assertThat(event.airline()).isEqualTo("United");
        assertThat(event.flightNumber()).isEqualTo("UA100");
        assertThat(event.departureAirport()).isEqualTo(AirportCode.of("SFO"));
        assertThat(event.arrivalAirport()).isEqualTo(AirportCode.of("JFK"));
        assertThat(event.departureDateTime()).isEqualTo(departure);
        assertThat(event.arrivalDateTime()).isEqualTo(arrival);
    }

    @Test
    void departureInPastThrowsDepartureNotInFuture() {
        ZonedTimestamp pastDeparture = zt(LocalDateTime.of(2026, 5, 15, 9, 0));
        ZonedTimestamp arrival = zt(LocalDateTime.of(2026, 5, 17, 14, 0));
        BookFlightCommand command = new BookFlightCommand(
                FlightId.random(), "United", "UA100",
                AirportCode.of("SFO"), pastDeparture,
                AirportCode.of("JFK"), arrival);

        assertThatThrownBy(() -> command.execute(new BookFlightContext(NOW)))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void arrivalBeforeDepartureThrowsInvalidDateRange() {
        ZonedTimestamp departure = zt(LocalDateTime.of(2026, 5, 17, 9, 0));
        ZonedTimestamp arrival = zt(LocalDateTime.of(2026, 5, 17, 8, 0));
        BookFlightCommand command = new BookFlightCommand(
                FlightId.random(), "United", "UA100",
                AirportCode.of("SFO"), departure,
                AirportCode.of("JFK"), arrival);

        assertThatThrownBy(() -> command.execute(new BookFlightContext(NOW)))
                .isInstanceOf(InvalidDateRange.class);
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, UTC);
    }
}
