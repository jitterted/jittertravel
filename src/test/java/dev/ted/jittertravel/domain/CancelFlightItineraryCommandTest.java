package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CancelFlightItineraryCommandTest {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant CANCELLED_ON = Instant.parse("2026-10-02T12:00:00Z");
    private static final FlightItineraryId ITINERARY = FlightItineraryId.of(UUID.randomUUID());

    private final FlightId out = FlightId.random();
    private final FlightId back = FlightId.random();

    @Test
    void everyLiveLegIsCancelledThenTheItineraryIsInOneStream() {
        var command = new CancelFlightItineraryCommand(ITINERARY, "Trip called off", CANCELLED_ON);

        assertThat(command.execute(context(true, leg(out, 10), leg(back, 20))).toList())
                .containsExactly(
                        new FlightCancelled(out, "Trip called off", CANCELLED_ON),
                        new FlightCancelled(back, "Trip called off", CANCELLED_ON),
                        new FlightItineraryCancelled(ITINERARY, "Trip called off", CANCELLED_ON));
    }

    @Test
    void aLegAlreadyCancelledOnItsOwnIsNotCancelledAgain() {
        // The context holds only live legs: `out` was cancelled singly and has been folded away.
        var command = new CancelFlightItineraryCommand(ITINERARY, "", CANCELLED_ON);

        assertThat(command.execute(context(true, leg(back, 20))).toList())
                .containsExactly(
                        new FlightCancelled(back, "", CANCELLED_ON),
                        new FlightItineraryCancelled(ITINERARY, "", CANCELLED_ON));
    }

    @Test
    void anItineraryWhoseLegsWereAllCancelledSinglyStillRecordsItsOwnCancellation() {
        var command = new CancelFlightItineraryCommand(ITINERARY, "", CANCELLED_ON);

        assertThat(command.execute(context(true)).toList())
                .containsExactly(new FlightItineraryCancelled(ITINERARY, "", CANCELLED_ON));
    }

    @Test
    void anItineraryThatIsNotLiveIsRefused() {
        var command = new CancelFlightItineraryCommand(ITINERARY, "", CANCELLED_ON);

        assertThatExceptionOfType(FlightItineraryNotFound.class)
                .isThrownBy(() -> command.execute(context(false)).toList())
                .withMessageContaining(ITINERARY.toString());
    }

    @Test
    void oneLegHavingDepartedRefusesTheWholeItineraryAndWritesNothing() {
        var command = new CancelFlightItineraryCommand(ITINERARY, "", CANCELLED_ON);
        // `out` left on 2026-10-01; `back` is still ahead.
        var departed = new ScheduledLeg(new ScheduledLegId.Flight(out),
                zoned(LocalDateTime.of(2026, 10, 1, 8, 0)), zoned(LocalDateTime.of(2026, 10, 1, 11, 0)));

        assertThatExceptionOfType(FlightItineraryHasDeparted.class)
                .isThrownBy(() -> command.execute(context(true, departed, leg(back, 20))).toList());
    }

    @Test
    void aLegDepartingAtThisVeryInstantHasDeparted() {
        // The same boundary a booking uses, from the other side: departure must be strictly after now.
        var command = new CancelFlightItineraryCommand(ITINERARY, "", CANCELLED_ON);
        var atNow = new ScheduledLeg(new ScheduledLegId.Flight(out),
                new ZonedTimestamp(NOW, CHICAGO), new ZonedTimestamp(NOW.plusSeconds(3600), CHICAGO));

        assertThatExceptionOfType(FlightItineraryHasDeparted.class)
                .isThrownBy(() -> command.execute(context(true, atNow)).toList());
    }

    @Test
    void aLegDepartingOneSecondFromNowHasNot() {
        var command = new CancelFlightItineraryCommand(ITINERARY, "", CANCELLED_ON);
        var justAfter = new ScheduledLeg(new ScheduledLegId.Flight(out),
                new ZonedTimestamp(NOW.plusSeconds(1), CHICAGO),
                new ZonedTimestamp(NOW.plusSeconds(3600), CHICAGO));

        assertThat(command.execute(context(true, justAfter)).toList())
                .hasSize(2);
    }

    @Test
    void anAbsentReasonBecomesEmptyRatherThanNull() {
        var command = new CancelFlightItineraryCommand(ITINERARY, null, CANCELLED_ON);

        assertThat(command.execute(context(true, leg(out, 10))).toList())
                .containsExactly(new FlightCancelled(out, "", CANCELLED_ON),
                        new FlightItineraryCancelled(ITINERARY, "", CANCELLED_ON));
    }

    private static CancelFlightItineraryContext context(boolean live, ScheduledLeg... legs) {
        return new CancelFlightItineraryContext(live, List.of(legs), NOW);
    }

    /** A leg departing on the given day of October 2026, well after {@code NOW}. */
    private static ScheduledLeg leg(FlightId flightId, int dayOfOctober) {
        return new ScheduledLeg(new ScheduledLegId.Flight(flightId),
                zoned(LocalDateTime.of(2026, 10, dayOfOctober, 8, 0)),
                zoned(LocalDateTime.of(2026, 10, dayOfOctober, 11, 0)));
    }

    private static ZonedTimestamp zoned(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, CHICAGO);
    }
}
