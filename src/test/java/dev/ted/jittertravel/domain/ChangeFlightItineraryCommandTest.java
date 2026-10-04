package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.domain.FlightItineraryRefused.LegRefusal;
import dev.ted.jittertravel.domain.ItineraryChangePlan.Kind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

class ChangeFlightItineraryCommandTest {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final FlightItineraryId ITINERARY = FlightItineraryId.of(UUID.randomUUID());

    // The booked trip: SFO->ORD on Oct 18, ORD->YOW the same afternoon, YOW->SFO on Oct 25.
    private final ItineraryLeg sfoOrd = booked("UA2091", "SFO", "ORD", 18, 6, 18, 12);
    private final ItineraryLeg ordYow = booked("UA3509", "ORD", "YOW", 18, 14, 18, 17);
    private final ItineraryLeg yowSfo = booked("UA1100", "YOW", "SFO", 25, 9, 25, 15);

    @Test
    void aMovedLegIsChangedAndTheRestAreLeftAlone() {
        ItineraryLeg later = pasted("UA2091", "SFO", "ORD", 18, 7, 18, 13);

        List<Event> events = command(later, copyOf(ordYow), copyOf(yowSfo)).execute(context()).toList();

        assertThat(events)
                .containsExactly(
                        new FlightChanged(sfoOrd.flightId(), "United Airlines", "UA2091",
                                sfoOrd.departureAirport(), later.departureDateTime(),
                                sfoOrd.arrivalAirport(), later.arrivalDateTime(), "Airline schedule change"),
                        new FlightItineraryChanged(ITINERARY,
                                List.of(sfoOrd.flightId(), ordYow.flightId(), yowSfo.flightId()),
                                "Airline schedule change", NOW));
    }

    @Test
    void aLegTheAirlineAddedIsBookedAndJoinsTheTrip() {
        ItineraryLeg extra = pasted("UA9", "YOW", "YYZ", 25, 6, 25, 7);

        List<Event> events = command(copyOf(sfoOrd), copyOf(ordYow), extra, copyOf(yowSfo))
                .execute(context()).toList();

        assertThat(events)
                .containsExactly(
                        extra.booked(),
                        new FlightItineraryChanged(ITINERARY,
                                List.of(sfoOrd.flightId(), ordYow.flightId(), extra.flightId(), yowSfo.flightId()),
                                "Airline schedule change", NOW));
    }

    @Test
    void aLegTheAirlineDroppedIsCancelledButStaysPartOfTheTrip() {
        List<Event> events = command(copyOf(sfoOrd), copyOf(yowSfo)).execute(context()).toList();

        assertThat(events)
                .containsExactly(
                        new FlightCancelled(ordYow.flightId(), "Airline schedule change", NOW),
                        new FlightItineraryChanged(ITINERARY,
                                List.of(sfoOrd.flightId(), ordYow.flightId(), yowSfo.flightId()),
                                "Airline schedule change", NOW));
    }

    @Test
    void aFlightNumberChangeOnTheSameRouteIsOneChangedLegNotARemovalAndAnAddition() {
        ItineraryLeg renumbered = pasted("UA777", "ORD", "YOW", 18, 14, 18, 17);

        ItineraryChangePlan plan = command(copyOf(sfoOrd), renumbered, copyOf(yowSfo)).plan(context());

        assertThat(plan.diffs())
                .extracting(ItineraryChangePlan.LegDiff::kind)
                .containsExactly(Kind.UNCHANGED, Kind.CHANGED, Kind.UNCHANGED);
        assertThat(plan.diffs().get(1).after().flightId())
                .as("the booked flight keeps its id")
                .isEqualTo(ordYow.flightId());
    }

    @Test
    void aLegMovedToAnotherDayIsMatchedByRoute() {
        ItineraryLeg nextDay = pasted("UA2091", "SFO", "ORD", 19, 6, 19, 12);

        ItineraryChangePlan plan = command(nextDay, copyOf(ordYow), copyOf(yowSfo)).plan(context());

        assertThat(plan.diffs())
                .extracting(ItineraryChangePlan.LegDiff::kind)
                .as("moved, not removed and added; the diff reads in departure order, so it follows ORD-YOW")
                .containsExactly(Kind.UNCHANGED, Kind.CHANGED, Kind.UNCHANGED);
    }

    @Test
    void aLegSharingOnlyTheDayWithABookedLegIsAnAdditionAndTheBookedLegIsRemoved() {
        ItineraryLeg unrelated = pasted("UA9", "ORD", "YYZ", 18, 15, 18, 16);

        ItineraryChangePlan plan = command(copyOf(sfoOrd), unrelated, copyOf(yowSfo)).plan(context());

        assertThat(plan.diffs())
                .extracting(ItineraryChangePlan.LegDiff::kind)
                .as("no flight number and no route in common, so ORD-YOW is dropped and UA9 is new")
                .containsExactly(Kind.UNCHANGED, Kind.REMOVED, Kind.ADDED, Kind.UNCHANGED);
    }

    @Test
    void aBookedLegMatchedOnceCannotBeMatchedAgainByARouteItShares() {
        ItineraryLeg sameRouteLater = pasted("UA55", "SFO", "ORD", 20, 6, 20, 12);

        ItineraryChangePlan plan = command(copyOf(sfoOrd), sameRouteLater, copyOf(ordYow), copyOf(yowSfo))
                .plan(context());

        assertThat(plan.diffs())
                .extracting(ItineraryChangePlan.LegDiff::kind)
                .as("UA2091 already claimed SFO-ORD, so UA55 is a new leg rather than a second move of it")
                .containsExactly(Kind.UNCHANGED, Kind.UNCHANGED, Kind.ADDED, Kind.UNCHANGED);
    }

    @Test
    void aPasteIdenticalToWhatIsBookedHasNothingToApplyAndIsNotRecorded() {
        ChangeFlightItineraryCommand same = command(copyOf(sfoOrd), copyOf(ordYow), copyOf(yowSfo));

        assertThat(same.plan(context()).hasChanges())
                .isFalse();
        assertThatExceptionOfType(FlightItineraryUnchanged.class)
                .isThrownBy(() -> same.execute(context()).toList());
    }

    @Test
    void aFlownLegGivenDifferentTimesRefusesTheWholeChange() {
        // Leg 1 left at 6:00 on Oct 18; "now" is the evening of Oct 18.
        Instant afterTakeoff = Instant.parse("2026-10-18T20:00:00Z");
        ItineraryLeg revised = pasted("UA2091", "SFO", "ORD", 18, 7, 18, 13);

        assertThatExceptionOfType(FlightItineraryRefused.class)
                .isThrownBy(() -> command(revised, copyOf(ordYow), copyOf(yowSfo))
                        .execute(context(afterTakeoff)).toList())
                .extracting(FlightItineraryRefused::refusals, list(LegRefusal.class))
                .extracting(LegRefusal::legNumber, refusal -> refusal.reason().getClass())
                .containsExactly(tuple(1, FlownLegContradicted.class));
    }

    @Test
    void aFlownLegMissingFromThePasteRefusesTheWholeChange() {
        Instant afterTakeoff = Instant.parse("2026-10-18T20:00:00Z");

        ItineraryChangePlan plan = command(copyOf(ordYow), copyOf(yowSfo)).plan(context(afterTakeoff));

        assertThat(plan.diffs())
                .filteredOn(ItineraryChangePlan.LegDiff::refused)
                .extracting(ItineraryChangePlan.LegDiff::kind, diff -> ((FlownLegContradicted) diff.refusal()).how())
                .containsExactly(tuple(Kind.REMOVED, FlownLegContradicted.How.MISSING_FROM_PASTE));
    }

    @Test
    void aFlownLegRepeatedUnchangedIsFineWhileLaterLegsMove() {
        Instant afterTakeoff = Instant.parse("2026-10-18T20:00:00Z");
        ItineraryLeg later = pasted("UA1100", "YOW", "SFO", 25, 10, 25, 16);

        assertThat(command(copyOf(sfoOrd), copyOf(ordYow), later).plan(context(afterTakeoff)).clean())
                .isTrue();
    }

    @Test
    void aLegCancelledOnItsOwnEarlierIsRefusedNotQuietlyBookedAgain() {
        // ORD->YOW was cancelled by itself; the airline's new email still lists it.
        ChangeFlightItineraryContext context = new ChangeFlightItineraryContext(true,
                List.of(sfoOrd, yowSfo), List.of(ordYow), scheduled(sfoOrd, yowSfo), NOW);
        ItineraryLeg relisted = pasted("UA3509", "ORD", "YOW", 18, 15, 18, 18);

        assertThatExceptionOfType(FlightItineraryRefused.class)
                .isThrownBy(() -> command(copyOf(sfoOrd), relisted, copyOf(yowSfo)).execute(context).toList())
                .extracting(FlightItineraryRefused::refusals, list(LegRefusal.class))
                .extracting(LegRefusal::legNumber, refusal -> refusal.reason().getClass())
                .containsExactly(tuple(2, LegCancelledEarlier.class));
    }

    @Test
    void aMovedLegCollidingWithAnotherBookingIsRefusedWithTheBlockingLeg() {
        ScheduledLeg train = new ScheduledLeg(new ScheduledLegId.Train(TrainTripId.of(UUID.randomUUID())),
                at(25, 11), at(25, 14));
        ItineraryChangePlan plan = command(copyOf(sfoOrd), copyOf(ordYow), pasted("UA1100", "YOW", "SFO", 25, 12, 25, 16))
                .plan(new ChangeFlightItineraryContext(true, List.of(sfoOrd, ordYow, yowSfo), List.of(),
                        new ScheduledLegs(append(scheduled(sfoOrd, ordYow, yowSfo).legs(), train)), NOW));

        assertThat(plan.diffs().get(2).refusal())
                .isInstanceOf(OverlappingLegRefused.class);
    }

    @Test
    void theItinerarysOwnOldLegsDoNotCountAsCollisions() {
        // The new UA2091 time overlaps where UA2091 used to be; that is the leg being rewritten.
        ItineraryLeg slightlyLater = pasted("UA2091", "SFO", "ORD", 18, 8, 18, 13);

        assertThat(command(slightlyLater, copyOf(ordYow), copyOf(yowSfo)).plan(context()).clean())
                .isTrue();
    }

    @Test
    void pastedLegsThatOverlapEachOtherAreRefusedNamingEachOther() {
        ItineraryLeg first = pasted("UA2091", "SFO", "ORD", 18, 6, 18, 15);
        ItineraryLeg second = copyOf(ordYow);

        ItineraryChangePlan plan = command(first, second, copyOf(yowSfo)).plan(context());

        assertThat(plan.diffs())
                .filteredOn(ItineraryChangePlan.LegDiff::refused)
                .extracting(ItineraryChangePlan.LegDiff::pasteNumber,
                        diff -> ((OverlapsAnotherItineraryLeg) diff.refusal()).otherLegNumber())
                .containsExactly(tuple(1, 2));
    }

    @Test
    void everyRefusalIsReportedTogether() {
        Instant afterTakeoff = Instant.parse("2026-10-18T20:00:00Z");
        ItineraryLeg revisedFlown = pasted("UA2091", "SFO", "ORD", 18, 7, 18, 13);
        ItineraryLeg backwards = pasted("UA1100", "YOW", "SFO", 25, 15, 25, 9);

        assertThatExceptionOfType(FlightItineraryRefused.class)
                .isThrownBy(() -> command(revisedFlown, copyOf(ordYow), backwards)
                        .execute(context(afterTakeoff)).toList())
                .extracting(FlightItineraryRefused::refusals, list(LegRefusal.class))
                .extracting(LegRefusal::legNumber, refusal -> refusal.reason().getClass())
                .containsExactly(
                        tuple(1, FlownLegContradicted.class),
                        tuple(3, InvalidDateRange.class));
    }

    @Test
    void aMovedLegThatNowDepartsInThePastIsRefused() {
        ItineraryLeg inThePast = pasted("UA1100", "YOW", "SFO", 2, 9, 2, 15);

        assertThat(command(copyOf(sfoOrd), copyOf(ordYow), inThePast).plan(context()).diffs().getFirst().refusal())
                .isInstanceOf(DepartureNotInFuture.class);
    }

    @Test
    void anItineraryThatIsNotLiveCannotBeChanged() {
        ChangeFlightItineraryContext cancelled = new ChangeFlightItineraryContext(false,
                List.of(), List.of(), ScheduledLegs.none(), NOW);

        assertThatExceptionOfType(FlightItineraryNotFound.class)
                .isThrownBy(() -> command(copyOf(sfoOrd)).execute(cancelled).toList());
    }

    private ChangeFlightItineraryCommand command(ItineraryLeg... pasted) {
        return new ChangeFlightItineraryCommand(ITINERARY, List.of(pasted));
    }

    private ChangeFlightItineraryContext context() {
        return context(NOW);
    }

    private ChangeFlightItineraryContext context(Instant now) {
        return new ChangeFlightItineraryContext(true, List.of(sfoOrd, ordYow, yowSfo), List.of(),
                scheduled(sfoOrd, ordYow, yowSfo), now);
    }

    private static ScheduledLegs scheduled(ItineraryLeg... legs) {
        return new ScheduledLegs(List.of(legs).stream()
                .map(leg -> new ScheduledLeg(new ScheduledLegId.Flight(leg.flightId()),
                        leg.departureDateTime(), leg.arrivalDateTime()))
                .toList());
    }

    private static List<ScheduledLeg> append(List<ScheduledLeg> legs, ScheduledLeg extra) {
        List<ScheduledLeg> all = new ArrayList<>(legs);
        all.add(extra);
        return all;
    }

    /** A leg as the paste gives it: a fresh id, because ids are minted before anything is matched. */
    private static ItineraryLeg copyOf(ItineraryLeg booked) {
        return new ItineraryLeg(FlightId.of(UUID.randomUUID()), booked.airline(), booked.flightNumber(),
                booked.departureAirport(), booked.departureDateTime(),
                booked.arrivalAirport(), booked.arrivalDateTime());
    }

    private static ItineraryLeg booked(String number, String from, String to,
                                       int departDay, int departHour, int arriveDay, int arriveHour) {
        return pasted(number, from, to, departDay, departHour, arriveDay, arriveHour);
    }

    private static ItineraryLeg pasted(String number, String from, String to,
                                       int departDay, int departHour, int arriveDay, int arriveHour) {
        return new ItineraryLeg(FlightId.of(UUID.randomUUID()), "United Airlines", number,
                AirportCode.of(from), at(departDay, departHour), AirportCode.of(to), at(arriveDay, arriveHour));
    }

    private static ZonedTimestamp at(int day, int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, day, hour, 0), CHICAGO);
    }
}
