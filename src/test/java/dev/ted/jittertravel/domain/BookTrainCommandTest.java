package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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

        List<TrainBooked> events = command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())).toList();

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

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void departureExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(NOW), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())))
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void arrivalBeforeDepartureThrowsInvalidDateRange() {
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(DEPARTURE), MANCHESTER, zt(DEPARTURE.minusMinutes(1)), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())))
                .isInstanceOf(InvalidDateRange.class);
    }

    @Test
    void arrivalSameDayAsDepartureIsValid() {
        LocalDateTime sameDayArrival = DEPARTURE.plusHours(2);
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(DEPARTURE), MANCHESTER, zt(sameDayArrival), "");

        assertThat(command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())).toList())
                .hasSize(1);
    }

    @Test
    void stationWithNoNameThrowsInvalidLocationEntryForThatEnd() {
        TrainStationAddress nameless = new TrainStationAddress("", "London", "UK", "");
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), nameless, zt(DEPARTURE), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid -> {
                    assertThat(invalid.locations())
                            .hasSize(1);
                    assertThat(invalid.locations().getFirst().role())
                            .isEqualTo(LocationRole.DEPARTURE);
                    assertThat(invalid.locations().getFirst().field())
                            .isEqualTo(LocationField.VENUE_NAME);
                });
    }

    @Test
    void stationNamePastedIntoTheCityThrowsInvalidLocationEntryForThatEnd() {
        TrainStationAddress pasted = new TrainStationAddress(
                "Frankfurt (Main) Hbf", "Frankfurt (Main) Hbf", "DE", "");
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), LONDON, zt(DEPARTURE), pasted, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid -> {
                    assertThat(invalid.locations())
                            .hasSize(1);
                    assertThat(invalid.locations().getFirst().role())
                            .isEqualTo(LocationRole.ARRIVAL);
                    assertThat(invalid.locations().getFirst().field())
                            .isEqualTo(LocationField.CITY);
                });
    }

    @Test
    void bothEndsAtFaultAreReportedTogether() {
        // One submit, one list. Reporting only the departure would mean fixing it, submitting
        // again, and meeting a fresh error about the arrival — which on screen is indistinguishable
        // from the first fix having done nothing.
        TrainStationAddress nameless = new TrainStationAddress("", "London", "UK", "");
        TrainStationAddress pasted = new TrainStationAddress(
                "Frankfurt (Main) Hbf", "Frankfurt (Main) Hbf", "DE", "");
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), nameless, zt(DEPARTURE), pasted, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid ->
                        assertThat(invalid.locations())
                                .extracting(InvalidLocationEntry::role, InvalidLocationEntry::field)
                                .containsExactly(
                                        tuple(LocationRole.DEPARTURE, LocationField.VENUE_NAME),
                                        tuple(LocationRole.ARRIVAL, LocationField.CITY)));
    }

    /**
     * <strong>One end can be wrong twice.</strong> Since 2026-09-20 {@code EnteredLocation}
     * answers with every rule the location breaks rather than the earliest, so a station entered
     * with neither a name nor a city contributes two entries and the form marks both inputs. The
     * per-field exclusivity is what keeps it to two: a blank city is never also asked whether it
     * looks like a building.
     */
    @Test
    void oneEndWithNoNameAndNoCityContributesBothProblems() {
        TrainStationAddress empty = new TrainStationAddress("", "", "UK", "");
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), empty, zt(DEPARTURE), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid ->
                        assertThat(invalid.locations())
                                .extracting(InvalidLocationEntry::role, InvalidLocationEntry::field)
                                .containsExactly(
                                        tuple(LocationRole.DEPARTURE, LocationField.VENUE_NAME),
                                        tuple(LocationRole.DEPARTURE, LocationField.CITY)));
    }

    @Test
    void locationIsCheckedBeforeTheTimes() {
        // Both are wrong; the location is the one reported, because a wrong place is the mistake
        // that looks right on the page.
        TrainStationAddress pasted = new TrainStationAddress("Frankfurt Hbf", "Frankfurt Hbf", "DE", "");
        BookTrainCommand command = new BookTrainCommand(
                TrainTripId.random(), pasted, zt(NOW.minusHours(1)), MANCHESTER, zt(ARRIVAL), "");

        assertThatThrownBy(() -> command.execute(new BookTrainContext(at(NOW), ScheduledLegs.none())))
                .isInstanceOf(InvalidTrainEntry.class);
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
    @Test
    void aTripOverlappingAnAlreadyBookedLegIsRefused() {
        ScheduledLegId blocking = new ScheduledLegId.Train(TrainTripId.random());
        ScheduledLegs booked = new ScheduledLegs(List.of(
                new ScheduledLeg(blocking, zt(DEPARTURE), zt(DEPARTURE.plusHours(2)))));

        BookTrainCommand command = command(DEPARTURE.plusHours(1), DEPARTURE.plusHours(3));

        assertThatExceptionOfType(OverlappingLegRefused.class)
                .isThrownBy(() -> command.execute(new BookTrainContext(at(NOW), booked)).toList())
                .satisfies(refusal -> assertThat(refusal.blocking().id()).isEqualTo(blocking));
    }

    @Test
    void aTripConnectingOutOfABookedLegIsAccepted() {
        // Arrive 11:00, depart 11:00 — the ordinary shape of a journey, and the case a closed
        // comparison would wrongly refuse.
        ScheduledLegs booked = new ScheduledLegs(List.of(
                new ScheduledLeg(new ScheduledLegId.Train(TrainTripId.random()),
                        zt(DEPARTURE), zt(DEPARTURE.plusHours(2)))));

        BookTrainCommand command = command(DEPARTURE.plusHours(2), DEPARTURE.plusHours(5));

        assertThat(command.execute(new BookTrainContext(at(NOW), booked)))
                .hasSize(1);
    }

    @Test
    void theDateRulesAreCheckedBeforeTheOverlap() {
        // An overlap is only a meaningful question once the window itself is valid; reporting a
        // collision for an inverted range would say nothing about what is actually wrong.
        ScheduledLegs booked = new ScheduledLegs(List.of(
                new ScheduledLeg(new ScheduledLegId.Train(TrainTripId.random()),
                        zt(DEPARTURE), zt(DEPARTURE.plusHours(14)))));

        BookTrainCommand command = command(DEPARTURE.plusHours(5), DEPARTURE.plusHours(3));

        assertThatExceptionOfType(InvalidDateRange.class)
                .isThrownBy(() -> command.execute(new BookTrainContext(at(NOW), booked)).toList());
    }

    /** A valid trip in a caller-chosen window, so the overlap cases can position it. */
    private static BookTrainCommand command(LocalDateTime departure, LocalDateTime arrival) {
        return new BookTrainCommand(TrainTripId.random(), LONDON, zt(departure),
                MANCHESTER, zt(arrival), "DB - ICE 610");
    }

}
