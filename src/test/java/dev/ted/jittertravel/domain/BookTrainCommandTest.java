package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTrainCommandTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 2, 10, 0);
    private static final LocalDateTime DEPARTURE = NOW.toLocalDate().plusWeeks(1).atTime(9, 0);
    private static final LocalDateTime ARRIVAL = DEPARTURE.plusHours(4);
    private static final TrainStationAddress LONDON =
            new TrainStationAddress("London Euston", "London", "UK", "");
    private static final TrainStationAddress MANCHESTER =
            new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");

    @Test
    void validCommandProducesTrainBookedEventWithAllFields() {
        BookTrainCommand command = validCommand();

        List<TrainBooked> events = command.execute(new BookTrainContext(NOW)).toList();

        assertThat(events)
                .hasSize(1);
        TrainBooked event = events.getFirst();
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
    void departureInPastThrowsDepartureNotInFuture() {
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, NOW.minusHours(1), MANCHESTER, ARRIVAL, "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(NOW)))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void departureExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, NOW, MANCHESTER, ARRIVAL, "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(NOW)))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void arrivalBeforeDepartureThrowsInvalidDateRange() {
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, DEPARTURE, MANCHESTER, DEPARTURE.minusMinutes(1), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(NOW)))
                .isInstanceOf(InvalidDateRange.class);
    }

    @Test
    void arrivalSameDayAsDepartureIsValid() {
        LocalDateTime sameDayArrival = DEPARTURE.plusHours(2);
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, DEPARTURE, MANCHESTER, sameDayArrival, "");

        assertThat(command.execute(new BookTrainContext(NOW)).toList())
                .hasSize(1);
    }

    private static BookTrainCommand validCommand() {
        return new BookTrainCommand(TrainTripId.random(), LONDON, DEPARTURE, MANCHESTER, ARRIVAL, "DB - ICE 610");
    }
}
