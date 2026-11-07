package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Real projectors, fed real events: the composer's job is to put two read models side by side, so
 * a stub of either would test nothing.
 */
class FlightTripsTest {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final BookedFlightsProjector flights = new BookedFlightsProjector();
    private final BookedItinerariesProjector itineraries = new BookedItinerariesProjector();
    private final FlightTrips trips = new FlightTrips(flights, itineraries);
    private final List<StoredEvent> log = new ArrayList<>();

    @Test
    void eachLegKnowsItsPlaceInTheBookingAndTheBookingsSize() {
        FlightId first = book(10, "SFO", "ORD");
        FlightId second = book(10, "ORD", "YOW");
        itinerary("MD7LKB", first, second);

        Map<FlightId, FlightTrip> result = trips.forList(listed(), NOW);

        assertThat(result.get(first))
                .extracting(trip -> trip.itinerary().confirmationCode(), FlightTrip::legNumber,
                        trip -> trip.itinerary().legCount())
                .containsExactly("MD7LKB", 1, 2);
        assertThat(result.get(second))
                .extracting(FlightTrip::legNumber, trip -> trip.itinerary().legCount())
                .containsExactly(2, 2);
    }

    @Test
    void aFlightEnteredByHandHasNoTrip() {
        FlightId byHand = book(10, "SFO", "LAX");

        assertThat(trips.forList(listed(), NOW))
                .doesNotContainKey(byHand);
    }

    @Test
    void aFilteredOutLegStillCountsInTheBookingsSize() {
        FlightId flown = book(1, "SFO", "ORD");
        FlightId ahead = book(10, "ORD", "YOW");
        itinerary("K3PQ9R", flown, ahead);

        Map<FlightId, FlightTrip> result = trips.forList(
                flights.views(TimeView.FUTURE, NOW), NOW);

        assertThat(result)
                .as("only the future leg is listed")
                .containsOnlyKeys(ahead);
        assertThat(result.get(ahead))
                .as("it is leg 2 of 2, so the reader knows another exists")
                .extracting(FlightTrip::legNumber, trip -> trip.itinerary().legCount())
                .containsExactly(2, 2);
    }

    @Test
    void departedLegsAreCountedEvenWhenTheFilterHidesThem() {
        FlightId flown1 = book(1, "SFO", "ORD");
        FlightId flown2 = book(1, "ORD", "YOW");
        FlightId ahead = book(10, "YOW", "ORD");
        itinerary("K3PQ9R", flown1, flown2, ahead);

        ItinerarySummary itinerary = trips.forList(flights.views(TimeView.FUTURE, NOW), NOW).get(ahead).itinerary();

        assertThat(itinerary.departedLegs())
                .isEqualTo(2);
        assertThat(itinerary.canBeCancelledWhole())
                .isFalse();
    }

    @Test
    void aDepartedLegCancelledOnItsOwnNoLongerCounts() {
        FlightId flown = book(1, "SFO", "ORD");
        FlightId ahead = book(10, "ORD", "YOW");
        itinerary("K3PQ9R", flown, ahead);
        append(new FlightCancelled(flown, "never flew", NOW));

        ItinerarySummary itinerary = trips.forList(flights.views(TimeView.FUTURE, NOW), NOW).get(ahead).itinerary();

        assertThat(itinerary.canBeCancelledWhole())
                .as("the command only looks at live legs, and so must the link")
                .isTrue();
    }

    @Test
    void aTripWhoseLegsAreAllAheadCanBeCancelledWhole() {
        FlightId a = book(10, "SFO", "ORD");
        FlightId b = book(12, "ORD", "SFO");
        itinerary("MD7LKB", a, b);

        assertThat(trips.forList(listed(), NOW).get(a).itinerary().canBeCancelledWhole())
                .isTrue();
    }

    @Test
    void afterARebookingTheLegsAreNumberedAmongThoseStillFlying() {
        FlightId sfoOrd = book(18, "SFO", "ORD");
        FlightId ordYow = book(18, 13, "ORD", "YOW");
        FlightId yowOrd = book(31, "YOW", "ORD");
        FlightId ordSfo = book(31, 13, "ORD", "SFO");
        FlightId yyzSfo = book(31, 15, "YYZ", "SFO");
        FlightItineraryId md7lkb = itinerary("MD7LKB", sfoOrd, ordYow, yowOrd, ordSfo).itineraryId();
        append(new FlightCancelled(yowOrd, "Rebooked", NOW));
        append(new FlightCancelled(ordSfo, "Rebooked", NOW));
        append(new FlightItineraryChanged(md7lkb, List.of(sfoOrd, ordYow, yowOrd, ordSfo, yyzSfo),
                "Rebooked", NOW));

        Map<FlightId, FlightTrip> result = trips.forList(flights.views(TimeView.ALL, CancelledView.SHOW, NOW), NOW);

        assertThat(List.of(sfoOrd, ordYow, yyzSfo))
                .as("1, 2, 3 of 3, not 1, 2, 5 of 5")
                .extracting(id -> result.get(id).legNumber())
                .containsExactly(1, 2, 3);
        assertThat(result.get(yyzSfo).itinerary().legCount())
                .isEqualTo(3);
        assertThat(List.of(yowOrd, ordSfo))
                .as("a dropped leg has no place among the legs still flying")
                .extracting(id -> result.get(id).legNumber())
                .containsExactly(0, 0);
    }

    @Test
    void theItineraryIsDescribedByItsJourneysStillFlying() {
        FlightId sfoOrd = book(18, "SFO", "ORD");
        FlightId ordYow = book(18, 13, "ORD", "YOW");
        FlightId yowOrd = book(31, "YOW", "ORD");
        FlightId yyzSfo = book(31, 15, "YYZ", "SFO");
        itinerary("MD7LKB", sfoOrd, ordYow, yowOrd, yyzSfo);
        append(new FlightCancelled(yowOrd, "Rebooked", NOW));

        ItinerarySummary itinerary = trips.forList(listed(), NOW).get(sfoOrd).itinerary();

        assertThat(itinerary.journeys())
                .as("the dropped YOW→ORD leg is not part of the route")
                .extracting(journey -> journey.from().code() + "→" + journey.to().code())
                .containsExactly("SFO→YOW", "YYZ→SFO");
        assertThat(itinerary.departs())
                .isEqualTo(at(18, 8));
        assertThat(itinerary.arrives())
                .isEqualTo(at(31, 18));
    }

    @Test
    void aCancelledItineraryIsDescribedByAllItsLegsAndCannotBeCancelledAgain() {
        FlightId out = book(18, "SFO", "ORD");
        FlightId back = book(31, "ORD", "SFO");
        FlightItineraryId md7lkb = itinerary("MD7LKB", out, back).itineraryId();
        append(new FlightCancelled(out, "", NOW));
        append(new FlightCancelled(back, "", NOW));
        append(new FlightItineraryCancelled(md7lkb, "", NOW));

        ItinerarySummary itinerary = trips.forList(
                flights.views(TimeView.ALL, CancelledView.SHOW, NOW), NOW).get(out).itinerary();

        assertThat(itinerary.cancelled())
                .isTrue();
        assertThat(itinerary.flying())
                .isFalse();
        assertThat(itinerary.legCount())
                .as("with nothing flying, the band still says what the itinerary was")
                .isEqualTo(2);
        assertThat(itinerary.canBeCancelledWhole())
                .isFalse();
    }

    @Test
    void anItineraryWhoseEveryLegWasCancelledOnItsOwnCannotBeCancelledWhole() {
        FlightId only = book(18, "SFO", "ORD");
        itinerary("MD7LKB", only);
        append(new FlightCancelled(only, "", NOW));

        ItinerarySummary itinerary = trips.forList(
                flights.views(TimeView.ALL, CancelledView.SHOW, NOW), NOW).get(only).itinerary();

        assertThat(itinerary.cancelled())
                .as("the itinerary itself was never cancelled")
                .isFalse();
        assertThat(itinerary.canBeCancelledWhole())
                .as("but nothing is left to cancel")
                .isFalse();
    }

    @Test
    void tripsAlternateTheirHueInOrderOfFirstAppearanceDownTheList() {
        FlightId outer1 = book(10, "SFO", "ORD");
        FlightId inner1 = book(11, "ORD", "YYZ");
        FlightId outer2 = book(12, "ORD", "SFO");
        FlightId inner2 = book(13, "YYZ", "ORD");
        FlightId third = book(14, "SFO", "LAX");
        itinerary("OUTER1", outer1, outer2);
        itinerary("INNER1", inner1, inner2);
        itinerary("THIRDX", third);

        Map<FlightId, FlightTrip> result = trips.forList(listed(), NOW);

        assertThat(result.get(outer1).itinerary().hue())
                .as("first trip down the list")
                .isEqualTo(0);
        assertThat(result.get(inner1).itinerary().hue())
                .as("second trip, nested in the first: a different hue")
                .isEqualTo(1);
        assertThat(result.get(outer2).itinerary().hue())
                .as("every leg of a trip wears that trip's hue")
                .isEqualTo(0);
        assertThat(result.get(inner2).itinerary().hue())
                .isEqualTo(1);
        assertThat(result.get(third).itinerary().hue())
                .as("a third trip reuses one: the code is the identifier")
                .isEqualTo(0);
    }

    @Test
    void anItineraryNestedInAGapIsTheOnlyThingThatStaysBookedWhenTheOuterOneIsCancelled() {
        FlightId out = book(10, "SFO", "ORD");
        FlightId toToronto = book(12, "YOW", "YYZ");
        FlightId fromToronto = book(14, "YYZ", "YOW");
        FlightId home = book(16, "IAD", "SFO");
        book(8, "SFO", "SEA");
        book(20, "SFO", "SEA");
        BookedItineraryView outer = itinerary("MD7LKB", out, home);
        itinerary("QX4TZN", toToronto, fromToronto);

        assertThat(trips.staysBooked(outer, NOW))
                .extracting(stay -> stay.flight().flightId(), StayingFlight::confirmationCode)
                .as("only flights between the outer legs, with their own trip's code")
                .containsExactly(tuple(toToronto, "QX4TZN"), tuple(fromToronto, "QX4TZN"));
    }

    @Test
    void aFlightEnteredByHandInTheGapStaysBookedWithNoCode() {
        FlightId out = book(10, "SFO", "ORD");
        book(12, "ORD", "DEN");
        FlightId home = book(16, "ORD", "SFO");
        BookedItineraryView outer = itinerary("MD7LKB", out, home);

        assertThat(trips.staysBooked(outer, NOW))
                .singleElement()
                .extracting(StayingFlight::confirmationCode)
                .isEqualTo("");
    }

    @Test
    void aCancelledFlightInTheGapStaysNothing() {
        FlightId out = book(10, "SFO", "ORD");
        FlightId inGap = book(12, "ORD", "DEN");
        FlightId home = book(16, "ORD", "SFO");
        BookedItineraryView outer = itinerary("MD7LKB", out, home);
        append(new FlightCancelled(inGap, "", NOW));

        assertThat(trips.staysBooked(outer, NOW))
                .as("a cancelled flight is already gone, so it is not 'staying'")
                .isEmpty();
    }

    @Test
    void theItinerarysOwnMiddleLegIsNotSomethingThatStays() {
        FlightId out = book(10, "SFO", "ORD");
        FlightId middle = book(12, "ORD", "YOW");
        FlightId home = book(16, "YOW", "SFO");
        BookedItineraryView outer = itinerary("MD7LKB", out, middle, home);

        assertThat(trips.staysBooked(outer, NOW))
                .as("a leg inside the span is still a leg of this itinerary")
                .isEmpty();
    }

    @Test
    void aCancelledLegDoesNotWidenTheSpanTheGapIsMeasuredOver() {
        FlightId cancelledEarly = book(8, "SFO", "SEA");
        FlightId out = book(12, "SFO", "ORD");
        FlightId home = book(16, "ORD", "SFO");
        BookedItineraryView outer = itinerary("MD7LKB", cancelledEarly, out, home);
        append(new FlightCancelled(cancelledEarly, "", NOW));
        book(10, "SFO", "LAX");

        assertThat(trips.staysBooked(outer, NOW))
                .as("the span starts at the first live leg, not at the one already cancelled")
                .isEmpty();
    }

    /**
     * The command writes a trip's legs in the same append as the itinerary that names them, so a
     * member the flights read model never saw is a corrupt log. Both composers say which, rather
     * than numbering the trip's legs without it.
     */
    @Test
    void anItineraryNamingAFlightNeverBookedFailsByNameInTheListAndInWhatStays() {
        FlightId booked = book(10, "SFO", "ORD");
        FlightId neverBooked = FlightId.random();
        BookedItineraryView broken = itinerary("MD7LKB", booked, neverBooked);
        String expected = "Itinerary " + broken.itineraryId().id() + " names flight " + neverBooked.id()
                          + ", which was never booked";

        assertThatThrownBy(() -> trips.forList(listed(), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(expected);
        assertThatThrownBy(() -> trips.staysBooked(broken, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(expected);
    }

    @Test
    void anItineraryWithNoLiveLegsLeavesNothingBehindToReport() {
        FlightId only = book(10, "SFO", "ORD");
        BookedItineraryView outer = itinerary("MD7LKB", only);
        book(12, "ORD", "DEN");
        append(new FlightCancelled(only, "", NOW));

        assertThat(trips.staysBooked(outer, NOW))
                .isEmpty();
    }

    private FlightId book(int dayOfOctober, String from, String to) {
        return book(dayOfOctober, 8, from, to);
    }

    private FlightId book(int dayOfOctober, int departureHour, String from, String to) {
        FlightId id = FlightId.random();
        append(new FlightBooked(id, "United Airlines", "UA" + log.size(),
                AirportCode.of(from), at(dayOfOctober, departureHour),
                AirportCode.of(to), at(dayOfOctober, departureHour + 3)));
        return id;
    }

    private BookedItineraryView itinerary(String code, FlightId... ids) {
        FlightItineraryId itineraryId = FlightItineraryId.of(UUID.randomUUID());
        append(new FlightItineraryBooked(itineraryId, "United Airlines", code, List.of(ids)));
        return itineraries.findLive(itineraryId).orElseThrow();
    }

    private void append(Event event) {
        StoredEvent stored = new StoredEvent(log.size() + 1L, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-09-01T00:00:00Z"), event, UUID.randomUUID());
        log.add(stored);
        flights.handle(Stream.of(stored));
        itineraries.handle(Stream.of(stored));
    }

    private List<BookedFlightView> listed() {
        return flights.views(TimeView.ALL, NOW);
    }

    private static ZonedTimestamp at(int dayOfOctober, int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, dayOfOctober, hour, 0), CHICAGO);
    }
}
