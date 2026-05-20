package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.web.ChangeFlightRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeFlightCommandTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 1, 12, 0);

    @Test
    void emitsFlightChangedWithFullSnapshotWhenValid() {
        ChangeFlightRequest dto = validRequest();

        List<FlightChanged> events = new ChangeFlightCommand()
                .execute(dto, true, NOW)
                .toList();

        assertThat(events).hasSize(1);
        FlightChanged event = events.getFirst();
        assertThat(event.flightId().id()).isEqualTo(UUID.fromString(dto.getFlightId()));
        assertThat(event.airline()).isEqualTo("United");
        assertThat(event.flightNumber()).isEqualTo("UA59");
        assertThat(event.departureAirport().code()).isEqualTo("SFO");
        assertThat(event.arrivalAirport().code()).isEqualTo("FRA");
        assertThat(event.departureDateTime()).isEqualTo(dto.getDepartureDateTime());
        assertThat(event.arrivalDateTime()).isEqualTo(dto.getArrivalDateTime());
        assertThat(event.reason()).isNull();
    }

    @Test
    void reasonForChangeIsCarriedOnTheEventWhenProvided() {
        ChangeFlightRequest dto = validRequest();
        dto.setReason("Schedule shifted by airline");

        FlightChanged event = new ChangeFlightCommand()
                .execute(dto, true, NOW)
                .findFirst()
                .orElseThrow();

        assertThat(event.reason()).isEqualTo("Schedule shifted by airline");
    }

    @Test
    void blankReasonIsNormalizedToNull() {
        ChangeFlightRequest dto = validRequest();
        dto.setReason("   ");

        FlightChanged event = new ChangeFlightCommand()
                .execute(dto, true, NOW)
                .findFirst()
                .orElseThrow();

        assertThat(event.reason()).isNull();
    }

    @Test
    void rejectsWhenFlightDoesNotExist() {
        ChangeFlightRequest dto = validRequest();

        assertThatThrownBy(() -> new ChangeFlightCommand().execute(dto, false, NOW))
                .isInstanceOf(FlightNotFound.class);
    }

    @Test
    void rejectsWhenDepartureIsNotInFuture() {
        ChangeFlightRequest dto = validRequest();
        dto.setDepartureDateTime(NOW.minusDays(1));
        dto.setArrivalDateTime(NOW.plusDays(1));

        assertThatThrownBy(() -> new ChangeFlightCommand().execute(dto, true, NOW))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void rejectsWhenArrivalIsNotAfterDeparture() {
        ChangeFlightRequest dto = validRequest();
        dto.setArrivalDateTime(dto.getDepartureDateTime());

        assertThatThrownBy(() -> new ChangeFlightCommand().execute(dto, true, NOW))
                .isInstanceOf(InvalidDateRange.class);
    }

    @Test
    void rejectsWhenAirportCodeIsInvalid() {
        ChangeFlightRequest dto = validRequest();
        dto.setDepartureAirport("SF"); // not 3 letters

        assertThatThrownBy(() -> new ChangeFlightCommand().execute(dto, true, NOW))
                .isInstanceOf(InvalidAirportCode.class);
    }

    private static ChangeFlightRequest validRequest() {
        ChangeFlightRequest dto = new ChangeFlightRequest();
        dto.setFlightId(UUID.randomUUID().toString());
        dto.setAirline("United");
        dto.setFlightNumber("UA59");
        dto.setDepartureAirport("SFO");
        dto.setDepartureDateTime(NOW.plusDays(7));
        dto.setArrivalAirport("FRA");
        dto.setArrivalDateTime(NOW.plusDays(7).plusHours(10));
        return dto;
    }
}
