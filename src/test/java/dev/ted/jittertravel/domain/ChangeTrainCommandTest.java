package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeTrainCommandTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 2, 10, 0);
    private static final LocalDateTime DEPARTURE = NOW.toLocalDate().plusWeeks(1).atTime(9, 0);
    private static final LocalDateTime ARRIVAL = DEPARTURE.plusHours(4);
    private static final TrainStationAddress LONDON =
            new TrainStationAddress("London Euston", "London", "UK", "");
    private static final TrainStationAddress MANCHESTER =
            new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");

    @Test
    void validChangeProducesTrainChangedEventWithAllFields() {
        ChangeTrainCommand command = validCommand();

        List<TrainChanged> events = command.execute(new ChangeTrainContext(true, NOW)).toList();

        assertThat(events)
                .hasSize(1);
        TrainChanged event = events.getFirst();
        assertThat(event.tripId())
                .isEqualTo(command.tripId());
        assertThat(event.departureStation())
                .isEqualTo(LONDON);
        assertThat(event.departureDateTime())
                .isEqualTo(DEPARTURE);
        assertThat(event.arrivalStation())
                .isEqualTo(MANCHESTER);
        assertThat(event.arrivalDateTime())
                .isEqualTo(ARRIVAL);
        assertThat(event.serviceId())
                .isEqualTo("DB - ICE 610");
    }

    @Test
    void changeRejectedWhenTrainDoesNotExist() {
        ChangeTrainCommand command = validCommand();

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(false, NOW)))
                .isInstanceOf(TrainNotFound.class);
    }

    @Test
    void departureInPastThrowsDepartureNotInFuture() {
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, NOW.minusHours(1), MANCHESTER, ARRIVAL, "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(true, NOW)))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void departureExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, NOW, MANCHESTER, ARRIVAL, "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(true, NOW)))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void arrivalBeforeDepartureThrowsInvalidDateRange() {
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, DEPARTURE, MANCHESTER, DEPARTURE.minusMinutes(1), "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(true, NOW)))
                .isInstanceOf(InvalidDateRange.class);
    }

    @Test
    void existenceIsCheckedBeforeDateValidation() {
        // A non-existent trip with otherwise-invalid dates still fails as TrainNotFound first.
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, NOW.minusHours(1), MANCHESTER, ARRIVAL, "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(false, NOW)))
                .isInstanceOf(TrainNotFound.class);
    }

    private static ChangeTrainCommand validCommand() {
        return new ChangeTrainCommand(TrainTripId.random(), LONDON, DEPARTURE, MANCHESTER, ARRIVAL, "DB - ICE 610");
    }
}