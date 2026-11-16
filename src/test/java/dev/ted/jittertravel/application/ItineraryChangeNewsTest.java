package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** D5 and D7 of {@code docs/FlightItineraryRebookingPlan.md}, with the plan's own examples. */
class ItineraryChangeNewsTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final ZoneId EASTERN = ZoneId.of("America/Toronto");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final ItineraryChangeNews news = new ItineraryChangeNews(new StaticAirportCityResolver(),
            new HomeCities(List.of("San Francisco", "Oakland", "San Jose")));

    private final ItineraryLeg sfoOrd = leg("SFO", 11, 2, 8, 0, PACIFIC, "ORD", 11, 2, 14, 10, CENTRAL);
    private final ItineraryLeg ordYow = leg("ORD", 11, 2, 16, 5, CENTRAL, "YOW", 11, 2, 19, 40, EASTERN);
    private final ItineraryLeg yowOrd = leg("YOW", 11, 9, 7, 0, EASTERN, "ORD", 11, 9, 8, 25, CENTRAL);
    private final ItineraryLeg ordSfo = leg("ORD", 11, 9, 10, 15, CENTRAL, "SFO", 11, 9, 12, 50, PACIFIC);

    @Test
    void aConnectionMovingFortyMinutesIsNotNews() {
        // UA 512 08:00 -> 08:40, still arriving ORD in time for the same YOW flight.
        ItineraryLeg later = leg("SFO", 11, 2, 8, 40, PACIFIC, "ORD", 11, 2, 14, 50, CENTRAL);

        assertThat(news.between(List.of(sfoOrd, ordYow, yowOrd, ordSfo), List.of(later, ordYow, yowOrd, ordSfo)))
                .isEmpty();
    }

    @Test
    void aReturnThatNowStartsInAnotherCityIsNews() {
        // MD7LKB: the return now leaves from Toronto, not Ottawa.
        ItineraryLeg yyzSfo = leg("YYZ", 11, 9, 10, 15, EASTERN, "SFO", 11, 9, 12, 45, PACIFIC);

        assertThat(news.between(List.of(sfoOrd, ordYow, yowOrd, ordSfo), List.of(sfoOrd, ordYow, yyzSfo)))
                .hasValue(new ItineraryChangeNews.Difference(ItineraryChangeNews.Kind.STARTS, 2, 2,
                        AirportCode.of("YOW"), AirportCode.of("YYZ"), yowOrd.departureDateTime(), yyzSfo.departureDateTime()));
    }

    @Test
    void he80ppsReturnNowStartsInFrankfurt() {
        ItineraryLeg berFra = leg("BER", 11, 26, 10, 40, BERLIN, "FRA", 11, 26, 11, 50, BERLIN);
        ItineraryLeg fraSfo = leg("FRA", 11, 26, 13, 30, BERLIN, "SFO", 11, 26, 16, 5, PACIFIC);
        ItineraryLeg sfoEwr = leg("SFO", 11, 7, 8, 15, PACIFIC, "EWR", 11, 7, 16, 38, NEW_YORK);
        ItineraryLeg ewrBer = leg("EWR", 11, 7, 17, 50, NEW_YORK, "BER", 11, 8, 7, 55, BERLIN);

        assertThat(news.between(List.of(sfoEwr, ewrBer, berFra, fraSfo), List.of(sfoEwr, ewrBer, fraSfo)))
                .map(ItineraryChangeNews.Difference::kind)
                .hasValue(ItineraryChangeNews.Kind.STARTS);
    }

    @Test
    void returningToAnotherHomeAirportIsNotNews() {
        ItineraryLeg ordOak = leg("ORD", 11, 9, 10, 15, CENTRAL, "OAK", 11, 9, 12, 55, PACIFIC);

        assertThat(news.between(List.of(sfoOrd, ordYow, yowOrd, ordSfo), List.of(sfoOrd, ordYow, yowOrd, ordOak)))
                .isEmpty();
    }

    @Test
    void aFirstDepartureMovingMoreThanTwoHoursIsNews() {
        ItineraryLeg muchEarlier = leg("SFO", 11, 2, 5, 50, PACIFIC, "ORD", 11, 2, 12, 0, CENTRAL);

        assertThat(news.between(List.of(sfoOrd, ordYow), List.of(muchEarlier, ordYow)))
                .map(ItineraryChangeNews.Difference::kind)
                .hasValue(ItineraryChangeNews.Kind.DEPARTS);
    }

    @Test
    void exactlyTwoHoursIsNotYetNews() {
        ItineraryLeg twoHoursEarlier = leg("SFO", 11, 2, 6, 0, PACIFIC, "ORD", 11, 2, 12, 10, CENTRAL);

        assertThat(news.between(List.of(sfoOrd, ordYow), List.of(twoHoursEarlier, ordYow)))
                .isEmpty();
    }

    @Test
    void aLastArrivalMovingMoreThanTwoHoursIsNews() {
        ItineraryLeg muchLater = leg("ORD", 11, 2, 16, 5, CENTRAL, "YOW", 11, 2, 22, 0, EASTERN);

        assertThat(news.between(List.of(sfoOrd, ordYow), List.of(sfoOrd, muchLater)))
                .map(ItineraryChangeNews.Difference::kind)
                .hasValue(ItineraryChangeNews.Kind.ARRIVES);
    }

    @Test
    void anEndInAnotherCityIsNews() {
        ItineraryLeg toMontreal = leg("ORD", 11, 2, 16, 5, CENTRAL, "YUL", 11, 2, 19, 40, EASTERN);

        assertThat(news.between(List.of(sfoOrd, ordYow), List.of(sfoOrd, toMontreal)))
                .map(ItineraryChangeNews.Difference::kind)
                .hasValue(ItineraryChangeNews.Kind.ENDS);
    }

    @Test
    void twoJourneysMergingIntoOneWithTheSameEndsIsNotNews() {
        // D7: ORD 10:00 Monday, YOW flight 11:00 Tuesday (two journeys) moves to 09:30 Tuesday... and
        // back the other way: one journey whose connection crosses 24 hours becomes two.
        ItineraryLeg sfoOrdMonday = leg("SFO", 11, 2, 4, 0, PACIFIC, "ORD", 11, 2, 10, 0, CENTRAL);
        ItineraryLeg ordYowTuesday = leg("ORD", 11, 3, 11, 0, CENTRAL, "YOW", 11, 3, 13, 50, EASTERN);
        ItineraryLeg ordYowEarlier = leg("ORD", 11, 3, 9, 30, CENTRAL, "YOW", 11, 3, 12, 20, EASTERN);

        assertThat(news.between(List.of(sfoOrdMonday, ordYowTuesday), List.of(sfoOrdMonday, ordYowEarlier)))
                .isEmpty();
    }

    @Test
    void aJourneyNothingBeforeOverlapsIsNews() {
        ItineraryLeg extraTrip = leg("SFO", 12, 1, 9, 0, PACIFIC, "SEA", 12, 1, 11, 10, PACIFIC);

        assertThat(news.between(List.of(sfoOrd, ordYow), List.of(sfoOrd, ordYow, extraTrip)))
                .as("the second of two journeys, with its own two ends")
                .hasValue(new ItineraryChangeNews.Difference(ItineraryChangeNews.Kind.ADDED, 2, 2,
                        AirportCode.of("SFO"), AirportCode.of("SEA"), extraTrip.departureDateTime(),
                        extraTrip.arrivalDateTime()));
    }

    @Test
    void aJourneyNothingAfterOverlapsIsNews() {
        assertThat(news.between(List.of(sfoOrd, ordYow, yowOrd, ordSfo), List.of(sfoOrd, ordYow)))
                .hasValue(new ItineraryChangeNews.Difference(ItineraryChangeNews.Kind.DROPPED, 2, 2,
                        AirportCode.of("YOW"), AirportCode.of("SFO"), yowOrd.departureDateTime(), ordSfo.arrivalDateTime()));
    }

    @Test
    void anUnchangedItineraryIsNotNews() {
        assertThat(news.between(List.of(sfoOrd, ordYow), List.of(sfoOrd, ordYow)))
                .isEmpty();
    }

    @Test
    void he80ppsChangedJourneyIsTheReturnFromItsOldStartToItsNewOneAndTheUnchangedOutboundIsNotListed() {
        ItineraryLeg sfoEwr = leg("SFO", 11, 7, 8, 15, PACIFIC, "EWR", 11, 7, 16, 38, NEW_YORK);
        ItineraryLeg ewrBer = leg("EWR", 11, 7, 17, 50, NEW_YORK, "BER", 11, 8, 7, 55, BERLIN);
        ItineraryLeg berFra = leg("BER", 11, 26, 10, 40, BERLIN, "FRA", 11, 26, 11, 50, BERLIN);
        ItineraryLeg fraSfo = leg("FRA", 11, 26, 13, 30, BERLIN, "SFO", 11, 26, 16, 5, PACIFIC);

        assertThat(news.changedJourneys(List.of(sfoEwr, ewrBer, berFra, fraSfo), List.of(sfoEwr, ewrBer, fraSfo)))
                .containsExactly(new ItineraryChangeNews.ChangedJourney(
                        new ItineraryChangeNews.JourneyEnds(AirportCode.of("BER"), berFra.departureDateTime(),
                                AirportCode.of("SFO"), fraSfo.arrivalDateTime()),
                        new ItineraryChangeNews.JourneyEnds(AirportCode.of("FRA"), fraSfo.departureDateTime(),
                                AirportCode.of("SFO"), fraSfo.arrivalDateTime())));
    }

    @Test
    void aJourneyWhoseEndsMovedLessThanTwoHoursIsStillListedBecauseTheEmailShowsExactTimes() {
        ItineraryLeg later = leg("SFO", 11, 2, 8, 40, PACIFIC, "ORD", 11, 2, 14, 50, CENTRAL);

        assertThat(news.changedJourneys(List.of(sfoOrd, ordYow), List.of(later, ordYow)))
                .extracting(ItineraryChangeNews.ChangedJourney::now)
                .extracting(ItineraryChangeNews.JourneyEnds::departs)
                .containsExactly(later.departureDateTime());
    }

    @Test
    void aJourneyWhoseOnlyChangeIsAConnectionIsNotListed() {
        ItineraryLeg ordYowLater = leg("ORD", 11, 2, 16, 35, CENTRAL, "YOW", 11, 2, 19, 40, EASTERN);

        assertThat(news.changedJourneys(List.of(sfoOrd, ordYow), List.of(sfoOrd, ordYowLater)))
                .isEmpty();
    }

    @Test
    void aJourneyNothingBeforeOverlapsHasNoOldEndsAndIsNotListed() {
        ItineraryLeg extraTrip = leg("SFO", 12, 1, 9, 0, PACIFIC, "SEA", 12, 1, 11, 10, PACIFIC);

        assertThat(news.changedJourneys(List.of(sfoOrd, ordYow), List.of(sfoOrd, ordYow, extraTrip)))
                .isEmpty();
    }

    private static ItineraryLeg leg(String from, int departMonth, int departDay, int departHour, int departMinute,
                                    ZoneId departZone, String to, int arriveMonth, int arriveDay, int arriveHour,
                                    int arriveMinute, ZoneId arriveZone) {
        return new ItineraryLeg(FlightId.random(), "United Airlines", "UA" + from + to, AirportCode.of(from),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, departMonth, departDay, departHour, departMinute), departZone),
                AirportCode.of(to),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, arriveMonth, arriveDay, arriveHour, arriveMinute), arriveZone));
    }
}
