package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTrainCommandTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/London");
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

        List<TrainBooked> events = command.execute(new BookTrainContext(at(NOW))).toList();

        assertThat(events)
                .hasSize(1);
        TrainBooked event = events.getFirst();
        assertThat(event.tripId())
                .isEqualTo(command.tripId());
        assertThat(event.departureStation())
                .isEqualTo(LONDON);
        assertThat(event.departureDateTime())
                .isEqualTo(zt(DEPARTURE));
        assertThat(event.arrivalStation())
                .isEqualTo(MANCHESTER);
        assertThat(event.arrivalDateTime())
                .isEqualTo(zt(ARRIVAL));
        assertThat(event.serviceId())
                .isEqualTo("DB - ICE 610");
    }

    @Test
    void departureInPastThrowsDepartureNotInFuture() {
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(NOW.minusHours(1)), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW))))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void departureExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(NOW), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW))))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void arrivalBeforeDepartureThrowsInvalidDateRange() {
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(DEPARTURE), MANCHESTER, zt(DEPARTURE.minusMinutes(1)), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW))))
                .isInstanceOf(InvalidDateRange.class);
    }

    @Test
    void arrivalSameDayAsDepartureIsValid() {
        LocalDateTime sameDayArrival = DEPARTURE.plusHours(2);
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(DEPARTURE), MANCHESTER, zt(sameDayArrival), "");

        assertThat(command.execute(new BookTrainContext(at(NOW))).toList())
                .hasSize(1);
    }

    @Test
    void stationWithNoNameThrowsInvalidLocationEntryForThatEnd() {
        TrainStationAddress nameless = new TrainStationAddress("", "London", "UK", "");
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), nameless, zt(DEPARTURE), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW))))
                .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid -> {
                    assertThat(invalid.role())
                            .isEqualTo(LocationRole.DEPARTURE);
                    assertThat(invalid.field())
                            .isEqualTo(LocationField.VENUE_NAME);
                });
    }

    @Test
    void stationNamePastedIntoTheCityThrowsInvalidLocationEntryForThatEnd() {
        TrainStationAddress pasted = new TrainStationAddress(
                "Frankfurt (Main) Hbf", "Frankfurt (Main) Hbf", "DE", "");
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(DEPARTURE), pasted, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW))))
                .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid -> {
                    assertThat(invalid.role())
                            .isEqualTo(LocationRole.ARRIVAL);
                    assertThat(invalid.field())
                            .isEqualTo(LocationField.CITY);
                });
    }

    @Test
    void locationIsCheckedBeforeTheTimes() {
        // Both are wrong; the location is the one reported, because a wrong place is the mistake
        // that looks right on the page.
        TrainStationAddress pasted = new TrainStationAddress("Frankfurt Hbf", "Frankfurt Hbf", "DE", "");
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), pasted, zt(NOW.minusHours(1)), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW))))
                .isInstanceOf(InvalidLocationEntry.class);
    }

    private static BookTrainCommand validCommand() {
        return new BookTrainCommand(TrainTripId.random(), LONDON, zt(DEPARTURE), MANCHESTER, zt(ARRIVAL), "DB - ICE 610");
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZONE);
    }

    private static Instant at(LocalDateTime local) {
        return local.atZone(ZONE).toInstant();
    }
}
