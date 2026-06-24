package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeFlightCommand;
import dev.ted.jittertravel.web.ChangeFlightRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeFlightHandlerTest {

    @Test
    void blankReasonIsNormalizedToEmpty() {
        ChangeFlightCommand command = new ChangeFlightHandler(new AirportZoneResolver())
                .handle(requestWithReason("   "));

        assertThat(command.reason()).isEmpty();
    }

    @Test
    void nullReasonIsNormalizedToEmpty() {
        ChangeFlightCommand command = new ChangeFlightHandler(new AirportZoneResolver())
                .handle(requestWithReason(null));

        assertThat(command.reason()).isEmpty();
    }

    @Test
    void nonBlankReasonIsTrimmedAndPreserved() {
        ChangeFlightCommand command = new ChangeFlightHandler(new AirportZoneResolver())
                .handle(requestWithReason("  Schedule shifted by airline  "));

        assertThat(command.reason()).isEqualTo("Schedule shifted by airline");
    }

    private ChangeFlightRequest requestWithReason(String reason) {
        ChangeFlightRequest request = new ChangeFlightRequest();
        request.setFlightId(UUID.randomUUID().toString());
        request.setAirline("United");
        request.setFlightNumber("UA59");
        request.setDepartureAirport("SFO");
        request.setDepartureDateTime(LocalDateTime.of(2026, 8, 1, 9, 0));
        request.setArrivalAirport("JFK");
        request.setArrivalDateTime(LocalDateTime.of(2026, 8, 1, 17, 0));
        request.setReason(reason);
        return request;
    }
}
