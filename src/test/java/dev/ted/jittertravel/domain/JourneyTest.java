package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JourneyTest {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");

    @Test
    void thirteenDaysOnTheGroundEndAJourney() {
        ItineraryLeg sfoOrd = leg("SFO", "ORD", at(18, 6, 10), at(18, 12, 45));
        ItineraryLeg ordYow = leg("ORD", "YOW", at(18, 13, 55), at(18, 17, 16));
        ItineraryLeg yowOrd = leg("YOW", "ORD", at(31, 16, 46), at(31, 18, 29));
        ItineraryLeg ordSfo = leg("ORD", "SFO", at(31, 21, 0), at(31, 23, 56));

        List<Journey> journeys = Journey.of(List.of(sfoOrd, ordYow, yowOrd, ordSfo));

        assertThat(journeys)
                .extracting(Journey::legs)
                .containsExactly(List.of(sfoOrd, ordYow), List.of(yowOrd, ordSfo));
        assertThat(journeys)
                .extracting(journey -> journey.from().code() + "→" + journey.to().code())
                .containsExactly("SFO→YOW", "YOW→SFO");
    }

    @Test
    void anOvernightLayoverIsStillAConnection() {
        ItineraryLeg landsLate = leg("SFO", "ORD", at(18, 15, 0), at(18, 21, 0));
        ItineraryLeg leavesNextMorning = leg("ORD", "YOW", at(19, 7, 30), at(19, 10, 45));

        assertThat(Journey.of(List.of(landsLate, leavesNextMorning)))
                .singleElement()
                .extracting(Journey::legs)
                .isEqualTo(List.of(landsLate, leavesNextMorning));
    }

    @Test
    void exactlyTwentyFourHoursOnTheGroundIsAConnectionAndAMinuteMoreIsNot() {
        ItineraryLeg lands = leg("SFO", "ORD", at(18, 8, 0), at(18, 10, 0));
        ItineraryLeg dayLater = leg("ORD", "YOW", at(19, 10, 0), at(19, 13, 0));
        ItineraryLeg minuteMore = leg("ORD", "YOW", at(19, 10, 1), at(19, 13, 0));

        assertThat(Journey.of(List.of(lands, dayLater)))
                .as("24 hours exactly")
                .hasSize(1);
        assertThat(Journey.of(List.of(lands, minuteMore)))
                .as("24 hours and a minute")
                .hasSize(2);
    }

    @Test
    void legsAreTakenInDepartureOrderWhateverOrderTheyArriveIn() {
        ItineraryLeg first = leg("SFO", "ORD", at(18, 6, 10), at(18, 12, 45));
        ItineraryLeg second = leg("ORD", "YOW", at(18, 13, 55), at(18, 17, 16));

        assertThat(Journey.of(List.of(second, first)))
                .singleElement()
                .extracting(Journey::from, Journey::to)
                .containsExactly(AirportCode.of("SFO"), AirportCode.of("YOW"));
    }

    @Test
    void noLegsAreNoJourneys() {
        assertThat(Journey.of(List.of()))
                .isEmpty();
    }

    @Test
    void aJourneyDepartsWithItsFirstLegAndArrivesWithItsLast() {
        ItineraryLeg first = leg("SFO", "ORD", at(18, 6, 10), at(18, 12, 45));
        ItineraryLeg last = leg("ORD", "YOW", at(18, 13, 55), at(18, 17, 16));

        Journey journey = Journey.of(List.of(first, last)).getFirst();

        assertThat(journey.departs())
                .isEqualTo(first.departureDateTime());
        assertThat(journey.arrives())
                .isEqualTo(last.arrivalDateTime());
    }

    private static ItineraryLeg leg(String from, String to, ZonedTimestamp departs, ZonedTimestamp arrives) {
        return new ItineraryLeg(FlightId.random(), "United Airlines", "UA1",
                AirportCode.of(from), departs, AirportCode.of(to), arrives);
    }

    private static ZonedTimestamp at(int dayOfOctober, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, dayOfOctober, hour, minute), CHICAGO);
    }
}
