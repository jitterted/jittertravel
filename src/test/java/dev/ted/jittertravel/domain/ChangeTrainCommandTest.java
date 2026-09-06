package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeTrainCommandTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/London");
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

        List<TrainChanged> events = command.execute(new ChangeTrainContext(true, at(NOW), ScheduledLegs.none())).toList();

        assertThat(events)
                .hasSize(1);
        TrainChanged event = events.getFirst();
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
    void changeRejectedWhenTrainDoesNotExist() {
        ChangeTrainCommand command = validCommand();

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(false, at(NOW), ScheduledLegs.none())))
                .isInstanceOf(TrainNotFound.class);
    }

    @Test
    void departureInPastThrowsDepartureNotInFuture() {
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, zt(NOW.minusHours(1)), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(true, at(NOW), ScheduledLegs.none())))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void departureExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, zt(NOW), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(true, at(NOW), ScheduledLegs.none())))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void arrivalBeforeDepartureThrowsInvalidDateRange() {
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, zt(DEPARTURE), MANCHESTER, zt(DEPARTURE.minusMinutes(1)), "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(true, at(NOW), ScheduledLegs.none())))
                .isInstanceOf(InvalidDateRange.class);
    }

    @Test
    void existenceIsCheckedBeforeDateValidation() {
        // A non-existent trip with otherwise-invalid dates still fails as TrainNotFound first.
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, zt(NOW.minusHours(1)), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(false, at(NOW), ScheduledLegs.none())))
                .isInstanceOf(TrainNotFound.class);
    }

    @Test
    void stationNamePastedIntoTheCityThrowsInvalidLocationEntryForThatEnd() {
        TrainStationAddress pasted = new TrainStationAddress(
                "Frankfurt (Main) Hbf", "Frankfurt (Main) Hbf", "DE", "");
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), pasted, zt(DEPARTURE), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(true, at(NOW), ScheduledLegs.none())))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid -> {
                    assertThat(invalid.locations())
                            .hasSize(1);
                    assertThat(invalid.locations().getFirst().role())
                            .isEqualTo(LocationRole.DEPARTURE);
                    assertThat(invalid.locations().getFirst().field())
                            .isEqualTo(LocationField.CITY);
                });
    }

    @Test
    void stationWithNoNameThrowsInvalidLocationEntryForThatEnd() {
        TrainStationAddress nameless = new TrainStationAddress("", "Manchester", "UK", "");
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), LONDON, zt(DEPARTURE), nameless, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(true, at(NOW), ScheduledLegs.none())))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid -> {
                    assertThat(invalid.locations())
                            .hasSize(1);
                    assertThat(invalid.locations().getFirst().role())
                            .isEqualTo(LocationRole.ARRIVAL);
                    assertThat(invalid.locations().getFirst().field())
                            .isEqualTo(LocationField.VENUE_NAME);
                });
    }

    @Test
    void existenceIsStillCheckedBeforeTheLocation() {
        // A trip that is gone reports that, not the bad city it was submitted with.
        TrainStationAddress pasted = new TrainStationAddress("Frankfurt Hbf", "Frankfurt Hbf", "DE", "");
        ChangeTrainCommand command = new ChangeTrainCommand(
                TrainTripId.random(), pasted, zt(DEPARTURE), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new ChangeTrainContext(false, at(NOW), ScheduledLegs.none())))
                .isInstanceOf(TrainNotFound.class);
    }

    private static ChangeTrainCommand validCommand() {
        return new ChangeTrainCommand(TrainTripId.random(), LONDON, zt(DEPARTURE), MANCHESTER, zt(ARRIVAL), "DB - ICE 610");
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZONE);
    }

    private static Instant at(LocalDateTime local) {
        return local.atZone(ZONE).toInstant();
    }
    @Test
    void changingATripDoesNotCollideWithTheVeryTripBeingChanged() {
        // The trap this rule would otherwise create: a change re-states the leg it replaces, so
        // without the self-exclusion no booked trip could ever be corrected again.
        ChangeTrainCommand command = validCommand();
        ScheduledLegs booked = new ScheduledLegs(List.of(new ScheduledLeg(
                new ScheduledLegId.Train(command.tripId()), zt(DEPARTURE), zt(ARRIVAL))));

        assertThat(command.execute(new ChangeTrainContext(true, at(NOW), booked)))
                .hasSize(1);
    }

    @Test
    void changingATripIntoAnotherLegsWindowIsRefused() {
        ChangeTrainCommand command = validCommand();
        ScheduledLegId other = new ScheduledLegId.Train(TrainTripId.random());
        ScheduledLegs booked = new ScheduledLegs(List.of(
                new ScheduledLeg(new ScheduledLegId.Train(command.tripId()), zt(DEPARTURE), zt(ARRIVAL)),
                new ScheduledLeg(other, zt(DEPARTURE.plusHours(1)), zt(ARRIVAL.plusHours(1)))));

        assertThatExceptionOfType(OverlappingLegRefused.class)
                .isThrownBy(() -> command.execute(new ChangeTrainContext(true, at(NOW), booked)).toList())
                .satisfies(refusal -> assertThat(refusal.blocking().id()).isEqualTo(other));
    }

}
