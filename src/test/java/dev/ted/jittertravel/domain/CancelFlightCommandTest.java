package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CancelFlightCommandTest {

    private static final Instant CANCELLED_ON = Instant.parse("2026-09-29T17:00:00Z");

    private final FlightId flightId = FlightId.random();

    @Test
    void cancellingALiveFlightEmitsFlightCancelledWithWhenItWasCancelled() {
        CancelFlightCommand command = new CancelFlightCommand(flightId, "Rebooked on UA58", CANCELLED_ON);

        assertThat(command.execute(new CancelFlightContext(true)))
                .containsExactly(new FlightCancelled(flightId, "Rebooked on UA58", CANCELLED_ON));
    }

    @Test
    void cancellingAFlightThatDoesNotExistIsRefused() {
        CancelFlightCommand command = new CancelFlightCommand(flightId, "", CANCELLED_ON);

        assertThatExceptionOfType(FlightNotFound.class)
                .isThrownBy(() -> command.execute(new CancelFlightContext(false)).toList())
                .withMessageContaining(flightId.toString());
    }

    @Test
    void anAbsentReasonBecomesEmptyRatherThanNull() {
        CancelFlightCommand command = new CancelFlightCommand(flightId, null, CANCELLED_ON);

        assertThat(command.execute(new CancelFlightContext(true)))
                .extracting(FlightCancelled::reason)
                .containsExactly("");
    }
}
