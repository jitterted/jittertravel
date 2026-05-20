package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.web.BookFlightRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookFlightCommandTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 16, 10, 0);

    @Test
    void executeProducesFlightBookedEventWithUppercasedAirportCodes() {
        BookFlightRequest request = validRequest();
        request.setDepartureAirport("sfo");
        request.setArrivalAirport("jfk");

        List<FlightBooked> events = new BookFlightCommand().execute(request, NOW).toList();

        assertThat(events).hasSize(1);
        FlightBooked event = events.getFirst();
        assertThat(event.airline()).isEqualTo("United");
        assertThat(event.flightNumber()).isEqualTo("UA100");
        assertThat(event.departureAirport()).isEqualTo(AirportCode.of("SFO"));
        assertThat(event.arrivalAirport()).isEqualTo(AirportCode.of("JFK"));
        assertThat(event.departureDateTime()).isEqualTo(NOW.plusDays(1));
        assertThat(event.arrivalDateTime()).isEqualTo(NOW.plusDays(1).plusHours(5));
    }

    @Test
    void departureInPastThrowsDepartureNotInFuture() {
        BookFlightRequest request = validRequest();
        request.setDepartureDateTime(NOW.minusHours(1));

        assertThatThrownBy(() -> new BookFlightCommand().execute(request, NOW))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void arrivalBeforeDepartureThrowsInvalidDateRange() {
        BookFlightRequest request = validRequest();
        request.setDepartureDateTime(NOW.plusDays(1));
        request.setArrivalDateTime(NOW.plusDays(1).minusHours(1));

        assertThatThrownBy(() -> new BookFlightCommand().execute(request, NOW))
                .isInstanceOf(InvalidDateRange.class);
    }

    @Test
    void invalidAirportCodeThrowsInvalidAirportCode() {
        BookFlightRequest request = validRequest();
        request.setDepartureAirport("SF"); // too short

        assertThatThrownBy(() -> new BookFlightCommand().execute(request, NOW))
                .isInstanceOf(InvalidAirportCode.class);
    }

    private BookFlightRequest validRequest() {
        BookFlightRequest request = new BookFlightRequest();
        request.setFlightId(UUID.randomUUID().toString());
        request.setAirline("United");
        request.setFlightNumber("UA100");
        request.setDepartureAirport("SFO");
        request.setDepartureDateTime(NOW.plusDays(1));
        request.setArrivalAirport("JFK");
        request.setArrivalDateTime(NOW.plusDays(1).plusHours(5));
        return request;
    }
}
