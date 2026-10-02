package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.domain.FlightItineraryRefused.LegRefusal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

class BookFlightItineraryCommandTest {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final FlightItineraryId ITINERARY = FlightItineraryId.of(UUID.randomUUID());

    @Test
    void everyLegIsBookedAndTheItineraryRecordsWhichFlightsBelongTogether() {
        ItineraryLeg out = leg(10, 8, 10, 11);
        ItineraryLeg back = leg(12, 8, 12, 11);

        List<Event> events = command(out, back).execute(context()).toList();

        assertThat(events)
                .containsExactly(
                        new FlightBooked(out.flightId(), "United Airlines", "UA1", out.departureAirport(),
                                out.departureDateTime(), out.arrivalAirport(), out.arrivalDateTime()),
                        new FlightBooked(back.flightId(), "United Airlines", "UA1", back.departureAirport(),
                                back.departureDateTime(), back.arrivalAirport(), back.arrivalDateTime()),
                        new FlightItineraryBooked(ITINERARY, "United Airlines", "MD7LKB",
                                List.of(out.flightId(), back.flightId())));
    }

    @Test
    void aConnectionIsNotAConflict() {
        // Landing at 11:00 and leaving at 11:00: the ordinary shape of a trip, and the half-open
        // predicate's reason for being.
        ItineraryLeg first = leg(10, 8, 10, 11);
        ItineraryLeg connecting = leg(10, 11, 10, 13);

        assertThat(command(first, connecting).execute(context()).toList())
                .hasSize(3);
    }

    @Test
    void twoLegsOfTheSameItineraryThatOverlapAreBothRefusedNamingEachOther() {
        ItineraryLeg first = leg(10, 8, 10, 11);
        ItineraryLeg clashing = leg(10, 10, 10, 12);

        assertThatExceptionOfType(FlightItineraryRefused.class)
                .isThrownBy(() -> command(first, clashing).execute(context()).toList())
                .extracting(FlightItineraryRefused::refusals, list(LegRefusal.class))
                .extracting(LegRefusal::legNumber,
                        refusal -> ((OverlapsAnotherItineraryLeg) refusal.reason()).otherLegNumber())
                .containsExactly(
                        tuple(1, 2),
                        tuple(2, 1));
    }

    @Test
    void aLegCollidingWithAFlightAlreadyBookedIsRefusedWithTheBlockingLeg() {
        ScheduledLeg booked = new ScheduledLeg(new ScheduledLegId.Flight(FlightId.of(UUID.randomUUID())),
                at(10, 9), at(10, 12));
        BookFlightContext context = new BookFlightContext(NOW, new ScheduledLegs(List.of(booked)));

        assertThatExceptionOfType(FlightItineraryRefused.class)
                .isThrownBy(() -> command(leg(10, 8, 10, 11)).execute(context).toList())
                .extracting(FlightItineraryRefused::refusals, list(LegRefusal.class))
                .singleElement()
                .extracting(LegRefusal::reason)
                .isInstanceOf(OverlappingLegRefused.class);
    }

    @Test
    void everyRefusalIsReportedTogetherEachAgainstItsOwnLeg() {
        // Leg 1 has already departed; leg 3 arrives before it departs; leg 2 is fine. Both refusals,
        // one throw — and a leg whose window is invalid is not also asked about overlaps.
        ItineraryLeg departed = leg(yesterday(8), yesterday(11));
        ItineraryLeg fine = leg(10, 8, 10, 11);
        ItineraryLeg backwards = leg(12, 11, 12, 8);

        assertThatExceptionOfType(FlightItineraryRefused.class)
                .isThrownBy(() -> command(departed, fine, backwards).execute(context()).toList())
                .extracting(FlightItineraryRefused::refusals, list(LegRefusal.class))
                .extracting(LegRefusal::legNumber, refusal -> refusal.reason().getClass())
                .containsExactly(
                        tuple(1, DepartureNotInFuture.class),
                        tuple(3, InvalidDateRange.class));
    }

    @Test
    void anItineraryHasAtLeastOneLeg() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BookFlightItineraryCommand(ITINERARY, "United Airlines", "MD7LKB", List.of()));
    }

    private static BookFlightItineraryCommand command(ItineraryLeg... legs) {
        return new BookFlightItineraryCommand(ITINERARY, "United Airlines", "MD7LKB", List.of(legs));
    }

    private static BookFlightContext context() {
        return new BookFlightContext(NOW, ScheduledLegs.none());
    }

    /** A leg in Chicago time on days of October 2026, all after NOW (Sep 30). */
    private static ItineraryLeg leg(int departDay, int departHour, int arriveDay, int arriveHour) {
        return leg(at(departDay, departHour), at(arriveDay, arriveHour));
    }

    private static ItineraryLeg leg(ZonedTimestamp departs, ZonedTimestamp arrives) {
        return new ItineraryLeg(FlightId.of(UUID.randomUUID()), "United Airlines", "UA1",
                AirportCode.of("ORD"), departs, AirportCode.of("SFO"), arrives);
    }

    private static ZonedTimestamp at(int octoberDay, int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, octoberDay, hour, 0), CHICAGO);
    }

    private static ZonedTimestamp yesterday(int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 29, hour, 0), CHICAGO);
    }
}
