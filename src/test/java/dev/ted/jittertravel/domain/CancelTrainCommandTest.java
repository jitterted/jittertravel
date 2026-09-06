package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CancelTrainCommandTest {

    private final TrainTripId tripId = TrainTripId.random();

    @Test
    void cancellingALiveTripEmitsTrainCancelled() {
        CancelTrainCommand command = new CancelTrainCommand(tripId, "Rebooked for the 17th");

        assertThat(command.execute(new CancelTrainContext(true)))
                .containsExactly(new TrainCancelled(tripId, "Rebooked for the 17th"));
    }

    @Test
    void cancellingATripThatDoesNotExistIsRefused() {
        CancelTrainCommand command = new CancelTrainCommand(tripId, "");

        assertThatExceptionOfType(TrainNotFound.class)
                .isThrownBy(() -> command.execute(new CancelTrainContext(false)).toList())
                .withMessageContaining(tripId.toString());
    }

    @Test
    void anAbsentReasonBecomesEmptyRatherThanNull() {
        CancelTrainCommand command = new CancelTrainCommand(tripId, null);

        assertThat(command.execute(new CancelTrainContext(true)))
                .extracting(TrainCancelled::reason)
                .containsExactly("");
    }
}
