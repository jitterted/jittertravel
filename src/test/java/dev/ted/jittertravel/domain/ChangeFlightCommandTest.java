package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ChangeFlightCommandTest {

    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void emitsFlightChangedWithFullSnapshotWhenValid() {
        ChangeFlightCommand command = validCommand();

        List<FlightChanged> events = command.execute(new ChangeFlightContext(true, NOW, ScheduledLegs.none())).toList();

        assertThat(events).hasSize(1);
        FlightChanged event = events.getFirst();
        assertThat(event.airline()).isEqualTo("United");
        assertThat(event.flightNumber()).isEqualTo("UA59");
        assertThat(event.departureAirport().code()).isEqualTo("SFO");
        assertThat(event.arrivalAirport().code()).isEqualTo("FRA");
        assertThat(event.reason()).isEmpty();
    }

    @Test
    void reasonForChangeIsCarriedOnTheEventWhenProvided() {
        ChangeFlightCommand command = new ChangeFlightCommand(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), zt(LocalDateTime.of(2026, 5, 8, 9, 0)),
                AirportCode.of("FRA"), zt(LocalDateTime.of(2026, 5, 8, 19, 0)),
                "Schedule shifted by airline");

        FlightChanged event = command.execute(new ChangeFlightContext(true, NOW, ScheduledLegs.none())).findFirst().orElseThrow();

        assertThat(event.reason()).isEqualTo("Schedule shifted by airline");
    }

    @Test
    void rejectsWhenFlightDoesNotExist() {
        assertThatThrownBy(() -> validCommand().execute(new ChangeFlightContext(false, NOW, ScheduledLegs.none())))
                .isInstanceOf(FlightNotFound.class);
    }

    @Test
    void rejectsWhenDepartureIsNotInFuture() {
        ChangeFlightCommand command = new ChangeFlightCommand(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), zt(LocalDateTime.of(2026, 4, 30, 9, 0)),
                AirportCode.of("FRA"), zt(LocalDateTime.of(2026, 5, 8, 19, 0)),
                null);

        assertThatThrownBy(() -> command.execute(new ChangeFlightContext(true, NOW, ScheduledLegs.none())))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    /**
     * Refused as the command is built, not when it runs: a backwards range needs no state to
     * judge, so it is invalid data rather than a domain refusal, and must never reach the command
     * log ({@code docs/archived/ValidateBeforeTheWalPlan.md}).
     */
    @Test
    void anArrivalNotAfterTheDepartureIsRefusedAsTheCommandIsBuilt() {
        ZonedTimestamp dep = zt(LocalDateTime.of(2026, 5, 8, 9, 0));

        assertThatThrownBy(() -> new ChangeFlightCommand(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), dep,
                AirportCode.of("FRA"), dep,
                null))
                .isInstanceOf(InvalidDateRange.class);
    }

    @Test
    void changingAFlightDoesNotCollideWithTheVeryFlightBeingChanged() {
        // The trap this rule would otherwise create: a change re-states the leg it replaces, so
        // without the self-exclusion no booked flight could ever be corrected again.
        ChangeFlightCommand command = validCommand();
        ScheduledLegs booked = new ScheduledLegs(List.of(new ScheduledLeg(
                new ScheduledLegId.Flight(command.flightId()),
                command.departureDateTime(), command.arrivalDateTime())));

        assertThat(command.execute(new ChangeFlightContext(true, NOW, booked)))
                .hasSize(1);
    }

    /**
     * A domain refusal: it needs every leg already on the books, so it runs in {@code execute}
     * and is recorded. Deleting the check survived mutation testing until this case (2026-10-23).
     */
    @Test
    void changingAFlightIntoAnotherLegsWindowIsRefused() {
        ChangeFlightCommand command = validCommand();
        ScheduledLegId other = new ScheduledLegId.Train(TrainTripId.random());
        ScheduledLegs booked = new ScheduledLegs(List.of(
                new ScheduledLeg(new ScheduledLegId.Flight(command.flightId()),
                        command.departureDateTime(), command.arrivalDateTime()),
                new ScheduledLeg(other,
                        zt(LocalDateTime.of(2026, 5, 8, 10, 0)), zt(LocalDateTime.of(2026, 5, 8, 20, 0)))));

        OverlappingLegRefused refusal = catchThrowableOfType(OverlappingLegRefused.class,
                () -> command.execute(new ChangeFlightContext(true, NOW, booked)).toList());

        assertThat(refusal)
                .as("a change into another leg's window is refused")
                .isNotNull();
        assertThat(refusal.blocking().id())
                .as("the refusal names the other leg, never the flight being changed")
                .isEqualTo(other);
    }

    private ChangeFlightCommand validCommand() {
        return new ChangeFlightCommand(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), zt(LocalDateTime.of(2026, 5, 8, 9, 0)),
                AirportCode.of("FRA"), zt(LocalDateTime.of(2026, 5, 8, 19, 0)),
                null);
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, UTC);
    }
}
