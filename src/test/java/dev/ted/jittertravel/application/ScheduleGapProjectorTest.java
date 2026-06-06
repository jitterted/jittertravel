package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleGapProjectorTest {

    // Three-letter codes usable with AirportCode + identity resolver (code == city name)
    private static final String LON = "LON";
    private static final String AMS = "AMS";
    private static final String BRU = "BRU";
    private static final String PRG = "PRG";

    // Fixed dates for deterministic tests
    private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);
    private static final LocalDate SEP_16 = LocalDate.of(2026, 9, 16);
    private static final LocalDate SEP_17 = LocalDate.of(2026, 9, 17);
    private static final LocalDate SEP_18 = LocalDate.of(2026, 9, 18);

    // Identity resolver: airport code is used directly as the city name
    private static final AirportCityResolver IDENTITY = code -> code;

    // -------------------------------------------------------------------------
    // Missing travel
    // -------------------------------------------------------------------------

    @Nested
    class MissingTravelDetection {

        @Test
        void noProblemsWithNoTravelEvents() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);

            assertThat(projector.problems()).isEmpty();
        }

        @Test
        void noMissingTravelWithOnlyOneLeg() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(stored(flight(LON, SEP_15.atTime(9, 0), AMS, SEP_15.atTime(11, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void noMissingTravelWhenConsecutiveLegsAreConnected() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(train("AMS", SEP_16.atTime(10, 0), "BRU", SEP_16.atTime(12, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void missingTravelReportedWhenLegsArriveAndDepartFromDifferentCities() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalDateTime flightArrival = SEP_15.atTime(11, 0);
            LocalDateTime nextDeparture = SEP_18.atTime(9, 0);
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, flightArrival)),
                    stored(flight(BRU, nextDeparture, PRG, SEP_18.atTime(11, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .containsExactly(new ScheduleProblem.MissingTravel("AMS", flightArrival, "BRU", nextDeparture));
        }

        @Test
        void multipleMissingTravelGapsAllReported() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(flight(BRU, SEP_16.atTime(10, 0), PRG, SEP_16.atTime(12, 0))),
                    stored(flight(LON, SEP_17.atTime(14, 0), AMS, SEP_17.atTime(16, 0)))));

            List<ScheduleProblem.MissingTravel> gaps = projector.problems().stream()
                    .filter(p -> p instanceof ScheduleProblem.MissingTravel)
                    .map(p -> (ScheduleProblem.MissingTravel) p)
                    .toList();
            assertThat(gaps).hasSize(2);
            assertThat(gaps).extracting(ScheduleProblem.MissingTravel::fromCity)
                    .containsExactly("AMS", "PRG");
            assertThat(gaps).extracting(ScheduleProblem.MissingTravel::toCity)
                    .containsExactly("BRU", "LON");
        }

        @Test
        void flightChangedReplacesOriginalLegForGapDetection() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            FlightId id = FlightId.random();
            // Originally LON→AMS (gap before next LON leg), changed to LON→LON (no gap)
            FlightBooked original = new FlightBooked(id, "Air", "A1",
                    AirportCode.of(LON), SEP_15.atTime(7, 0),
                    AirportCode.of(AMS), SEP_15.atTime(9, 0));
            FlightChanged changed = new FlightChanged(id, "Air", "A1",
                    AirportCode.of(LON), SEP_15.atTime(7, 0),
                    AirportCode.of(LON), SEP_15.atTime(9, 0), null);
            FlightBooked next = new FlightBooked(FlightId.random(), "Air", "A2",
                    AirportCode.of(LON), SEP_16.atTime(10, 0),
                    AirportCode.of(AMS), SEP_16.atTime(12, 0));

            projector.handle(Stream.of(stored(original), stored(changed), stored(next)));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void airportCodesResolvedToCityNamesForConnectivityCheck() {
            // LHR → "London" via StaticAirportCityResolver; train departs from "London"
            ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());
            projector.handle(Stream.of(
                    stored(new FlightBooked(FlightId.random(), "BA", "BA100",
                            AirportCode.of("SFO"), SEP_15.atTime(10, 0),
                            AirportCode.of("LHR"), SEP_16.atTime(6, 0))),
                    stored(train("London", SEP_17.atTime(9, 0), "Amsterdam", SEP_17.atTime(13, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void unknownAirportCodeUsedDirectlyAsCityName() {
            // "ZZZ" is not in the static map — resolver returns "ZZZ" as-is
            ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());
            projector.handle(Stream.of(
                    stored(new FlightBooked(FlightId.random(), "Air", "X1",
                            AirportCode.of("SFO"), SEP_15.atTime(10, 0),
                            AirportCode.of("ZZZ"), SEP_15.atTime(14, 0))),
                    stored(new FlightBooked(FlightId.random(), "Air", "X2",
                            AirportCode.of("ZZZ"), SEP_16.atTime(9, 0),
                            AirportCode.of("AMS"), SEP_16.atTime(11, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void bothAirportsForSameCityTreatedAsConnected() {
            // JFK and LGA both map to "New York" — consecutive legs still connected
            ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());
            projector.handle(Stream.of(
                    stored(new FlightBooked(FlightId.random(), "AA", "AA1",
                            AirportCode.of("SFO"), SEP_15.atTime(8, 0),
                            AirportCode.of("JFK"), SEP_15.atTime(16, 0))),
                    stored(new FlightBooked(FlightId.random(), "AA", "AA2",
                            AirportCode.of("LGA"), SEP_17.atTime(9, 0),
                            AirportCode.of("LHR"), SEP_18.atTime(6, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Missing hotel
    // -------------------------------------------------------------------------

    @Nested
    class MissingHotelDetection {

        @Test
        void noMissingHotelWhenHotelCoversArrivalNight() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(hotel("AMS", SEP_15, SEP_18))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .isEmpty();
        }

        @Test
        void noMissingHotelWhenArrivalCityHasNoOnwardTravel() {
            // Final destination (home city) with no booked departure — no overnight stay needed
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(AMS, SEP_15.atTime(7, 0), LON, SEP_15.atTime(9, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .isEmpty();
        }

        @Test
        void missingHotelReportedWhenNoHotelBookedInArrivalCity() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(flight(AMS, SEP_16.atTime(10, 0), BRU, SEP_16.atTime(11, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactly(new ScheduleProblem.MissingHotel("AMS", SEP_15, SEP_16, ""));
        }

        @Test
        void noMissingHotelWhenHotelCheckedInBeforeFlightArrival() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(hotel("AMS", SEP_14, SEP_18))));  // checked in the day before

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .isEmpty();
        }

        @Test
        void hotelInWrongCityDoesNotCoverArrival() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(flight(AMS, SEP_18.atTime(10, 0), LON, SEP_18.atTime(12, 0))),
                    stored(hotel("BRU", SEP_15, SEP_18))));  // hotel in Brussels, not Amsterdam

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactly(new ScheduleProblem.MissingHotel("AMS", SEP_15, SEP_18, ""));
        }

        @Test
        void multiNightStayWithNoHotelConsolidatedIntoSingleEntry() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            // Arrive AMS Sep 15, leave Sep 18 → need nights Sep 15, 16, 17 → one entry
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(flight(AMS, SEP_18.atTime(10, 0), BRU, SEP_18.atTime(11, 0))),
                    stored(hotel("BRU", SEP_18, SEP_20))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactly(new ScheduleProblem.MissingHotel("AMS", SEP_15, SEP_18, ""));
        }

        @Test
        void onlyUncoveredNightsReportedWhenHotelCoversPartially() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            // Hotel covers Sep 16-18 (nights 16, 17) — Sep 15 uncovered, one-night gap
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(flight(AMS, SEP_18.atTime(10, 0), BRU, SEP_18.atTime(11, 0))),
                    stored(hotel("AMS", SEP_16, SEP_18)),
                    stored(hotel("BRU", SEP_18, SEP_20))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactly(new ScheduleProblem.MissingHotel("AMS", SEP_15, SEP_16, ""));
        }

        @Test
        void noHotelNeededForSameDayArrivalAndDeparture() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            // Arrive AMS 10 AM, depart AMS 4 PM same day — no hotel needed for AMS
            // Hotel in BRU covers the overnight destination
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(8, 0), AMS, SEP_15.atTime(10, 0))),
                    stored(flight(AMS, SEP_15.atTime(16, 0), BRU, SEP_15.atTime(17, 0))),
                    stored(hotel("BRU", SEP_15, SEP_17))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .isEmpty();
        }

        @Test
        void conferenceDatesWithoutHotelConsolidatedWithConferenceName() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conference("Amsterdam", SEP_15, SEP_17))));

            // Conference Sep 15–17 → nights Sep 15, 16 → one consolidated entry with conference name
            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactly(new ScheduleProblem.MissingHotel("Amsterdam", SEP_15, SEP_17, "Conf"));
        }

        @Test
        void conferenceDatesWithHotelGenerateNoMissingHotel() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conference("Amsterdam", SEP_15, SEP_17)),
                    stored(hotel("Amsterdam", SEP_15, SEP_17))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .isEmpty();
        }

        @Test
        void overlappingLegAndConferenceNightsConsolidatedIntoOneEntry() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            // Flight arrives AMS Sep 15 (leg covers Sep 15 night)
            // Conference AMS Sep 15–17 (covers Sep 15, 16 nights)
            // Deduplicated → nights Sep 15, 16 → one consolidated entry with conference name
            projector.handle(Stream.of(
                    stored(flight(LON, SEP_15.atTime(7, 0), AMS, SEP_15.atTime(9, 0))),
                    stored(conference("AMS", SEP_15, SEP_17))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactly(new ScheduleProblem.MissingHotel("AMS", SEP_15, SEP_17, "Conf"));
        }
    }

    // -------------------------------------------------------------------------
    // Conference-city overlap: travel city nights must not overlap conference city nights
    // -------------------------------------------------------------------------

    @Nested
    class ConferenceCityOverlapDetection {

        @Test
        void conferenceInDifferentCityExcludesOverlappingNightsFromArrivalCity() {
            // Arrive LON Sep 15, conference in Steventon Sep 15-17, depart LON Sep 18
            // → LON only needs Sep 17 (post-conference); Steventon Sep 15-16 uncovered
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(AMS, SEP_15.atTime(7, 0), LON, SEP_15.atTime(9, 0))),
                    stored(flight(LON, SEP_18.atTime(10, 0), AMS, SEP_18.atTime(12, 0))),
                    stored(hotel("AMS", SEP_18, SEP_19)),
                    stored(conference("Steventon", SEP_15, SEP_17))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactlyInAnyOrder(
                            new ScheduleProblem.MissingHotel(LON, SEP_17, SEP_18, ""),
                            new ScheduleProblem.MissingHotel("Steventon", SEP_15, SEP_17, "Conf"));
        }

        @Test
        void conferenceCoversAllArrivalCityNightsLeavingNoArrivalCityProblem() {
            // Arrive LON Sep 15, conference in Steventon Sep 15-17, depart LON Sep 17
            // → LON has no uncovered nights; only Steventon Sep 15-16 is uncovered
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(AMS, SEP_15.atTime(7, 0), LON, SEP_15.atTime(9, 0))),
                    stored(flight(LON, SEP_17.atTime(10, 0), AMS, SEP_17.atTime(12, 0))),
                    stored(hotel("AMS", SEP_17, SEP_19)),
                    stored(conference("Steventon", SEP_15, SEP_17))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactly(new ScheduleProblem.MissingHotel("Steventon", SEP_15, SEP_17, "Conf"));
        }

        @Test
        void hotelInConferenceCityOnPreConferenceNightDoesNotRequireHotelInArrivalCity() {
            // Arrive LON Sep 15; hotel in Steventon Sep 15-19; conference in Steventon Sep 16-19.
            // Sep 15 is pre-conference but in the conference city — traveler is in Steventon.
            // London Sep 15 should not be flagged; Steventon nights Sep 16-18 covered by hotel.
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(train("BRU", SEP_15.atTime(10, 0), LON, SEP_15.atTime(12, 0))),
                    stored(hotel("Steventon", SEP_15, SEP_19)),
                    stored(conference("Steventon", SEP_16, SEP_19)),
                    stored(flight(LON, SEP_19.atTime(14, 0), AMS, SEP_19.atTime(16, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .isEmpty();
        }

        @Test
        void arrivalCityNightsSurroundingConferenceSplitIntoTwoSeparateEntries() {
            // Arrive LON Sep 15, conference in Steventon Sep 16-18, depart LON Sep 19
            // → LON needs Sep 15 (pre-conference) and Sep 18 (post-conference) as two entries
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(flight(AMS, SEP_15.atTime(7, 0), LON, SEP_15.atTime(9, 0))),
                    stored(flight(LON, SEP_19.atTime(10, 0), AMS, SEP_19.atTime(12, 0))),
                    stored(hotel("AMS", SEP_19, SEP_21)),
                    stored(conference("Steventon", SEP_16, SEP_18))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingHotel)
                    .containsExactlyInAnyOrder(
                            new ScheduleProblem.MissingHotel(LON, SEP_15, SEP_16, ""),
                            new ScheduleProblem.MissingHotel("Steventon", SEP_16, SEP_18, "Conf"),
                            new ScheduleProblem.MissingHotel(LON, SEP_18, SEP_19, ""));
        }
    }

    // -------------------------------------------------------------------------
    // Gathering scheduling conflicts
    // -------------------------------------------------------------------------

    @Nested
    class GatheringConflictDetection {

        @Test
        void noConflictWhenOnlyOneGathering() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(stored(gathering("LJC", SEP_15, LocalTime.of(18, 0), LocalTime.of(21, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.SchedulingConflict)
                    .isEmpty();
        }

        @Test
        void noConflictWhenGatheringsAreOnDifferentDates() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(gathering("LJC", SEP_15, LocalTime.of(18, 0), LocalTime.of(21, 0))),
                    stored(gathering("MJUG", SEP_16, LocalTime.of(18, 30), LocalTime.of(21, 30)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.SchedulingConflict)
                    .isEmpty();
        }

        @Test
        void noConflictWhenGatheringsOnSameDayDoNotOverlap() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(gathering("LJC", SEP_15, LocalTime.of(18, 0), LocalTime.of(20, 0))),
                    stored(gathering("MJUG", SEP_15, LocalTime.of(20, 0), LocalTime.of(22, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.SchedulingConflict)
                    .isEmpty();
        }

        @Test
        void conflictReportedWhenGatheringsOnSameDayOverlap() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalTime start1 = LocalTime.of(18, 0);
            LocalTime end1 = LocalTime.of(21, 0);
            LocalTime start2 = LocalTime.of(19, 30);
            LocalTime end2 = LocalTime.of(22, 0);
            projector.handle(Stream.of(
                    stored(gathering("LJC", SEP_15, start1, end1)),
                    stored(gathering("MJUG", SEP_15, start2, end2))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.SchedulingConflict)
                    .hasSize(1);
            ScheduleProblem.SchedulingConflict conflict = projector.problems().stream()
                    .filter(p -> p instanceof ScheduleProblem.SchedulingConflict)
                    .map(p -> (ScheduleProblem.SchedulingConflict) p)
                    .findFirst().orElseThrow();
            assertThat(conflict.date())
                    .isEqualTo(SEP_15);
        }

        @Test
        void conflictReportedWhenOneGatheringContainsAnother() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(gathering("Long event", SEP_15, LocalTime.of(17, 0), LocalTime.of(23, 0))),
                    stored(gathering("Short event", SEP_15, LocalTime.of(19, 0), LocalTime.of(21, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.SchedulingConflict)
                    .hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // Ordering
    // -------------------------------------------------------------------------

    @Nested
    class ProblemOrdering {

        @Test
        void problemsOrderedByFirstDateAscendingNotByCityName() {
            // AMS (alphabetically first) has check-in Sep 16; BRU has check-in Sep 15.
            // Without date sort, city-alphabetical ordering produces [AMS Sep 16, BRU Sep 15] — wrong.
            // With date sort, output must be [BRU Sep 15, AMS Sep 16].
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conference(AMS, SEP_16, SEP_18)),
                    stored(conference(BRU, SEP_15, SEP_16))));

            assertThat(projector.problems())
                    .extracting(p -> ((ScheduleProblem.MissingHotel) p).checkIn())
                    .containsExactly(SEP_15, SEP_16);
        }

        @Test
        void hotelProblemWithEarlierDateOrdersBeforeMissingTravel() {
            // Hotel problem check-in Sep 15, travel gap arrives Sep 16 — hotel should sort first.
            // Without date sort, MissingTravel is always emitted before MissingHotel — wrong here.
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalDateTime gapArrival = SEP_16.atTime(9, 0);
            LocalDateTime nextDep = SEP_18.atTime(9, 0);
            // Flight departs from AMS (conference city) so no conference-departure gap is triggered;
            // the travel gap BRU→AMS is detected by the consecutive-leg check.
            projector.handle(Stream.of(
                    stored(conference(AMS, SEP_15, SEP_16)),                          // hotel Sep 15
                    stored(flight(AMS, SEP_16.atTime(7, 0), BRU, gapArrival)),        // arrives BRU Sep 16
                    stored(flight(AMS, nextDep, PRG, SEP_18.atTime(11, 0))),          // departs AMS (travel gap BRU→AMS)
                    stored(hotel("BRU", SEP_16, SEP_18)),
                    stored(hotel("PRG", SEP_18, SEP_20))));

            List<ScheduleProblem> problems = projector.problems();
            assertThat(problems).hasSize(2);
            assertThat(problems.get(0))
                    .isInstanceOf(ScheduleProblem.MissingHotel.class);
            assertThat(((ScheduleProblem.MissingHotel) problems.get(0)).checkIn()).isEqualTo(SEP_15);
            assertThat(problems.get(1)).isInstanceOf(ScheduleProblem.MissingTravel.class);
        }
    }

    // -------------------------------------------------------------------------
    // Missing travel to/from conference venue
    // -------------------------------------------------------------------------

    @Nested
    class MissingTravelToFromConference {

        @Test
        void missingTravelDetectedWhenLastLegBeforeConferenceIsInDifferentCity() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalDateTime trainArrival = SEP_15.atTime(11, 57);
            projector.handle(Stream.of(
                    stored(train("BRU", SEP_15.atTime(10, 0), LON, trainArrival)),
                    stored(conference("Steventon", SEP_16, SEP_17))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .containsExactly(new ScheduleProblem.MissingTravel(
                            LON, trainArrival, "Steventon", SEP_16.atStartOfDay()));
        }

        @Test
        void noMissingTravelToConferenceWhenLastLegArrivesInConferenceCity() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(train(LON, SEP_15.atTime(10, 0), "Steventon", SEP_15.atTime(13, 0))),
                    stored(conference("Steventon", SEP_16, SEP_17))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void missingTravelDetectedWhenFirstLegAfterConferenceIsInDifferentCity() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalDateTime flightDeparture = SEP_18.atTime(10, 0);
            projector.handle(Stream.of(
                    stored(conference("Steventon", SEP_16, SEP_17)),
                    stored(flight(LON, flightDeparture, AMS, SEP_18.atTime(12, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .containsExactly(new ScheduleProblem.MissingTravel(
                            "Steventon", SEP_17.atStartOfDay(), LON, flightDeparture));
        }

        @Test
        void noMissingTravelFromConferenceWhenFirstLegDepartsFromConferenceCity() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conference("Steventon", SEP_16, SEP_17)),
                    stored(train("Steventon", SEP_17.atTime(14, 0), LON, SEP_17.atTime(16, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void noMissingTravelToConferenceWhenConnectingLegDepartsBeforeConferenceStartButArrivesAfter() {
            // Train departs LON 7:51 AM and arrives AMS 9:36 AM; conference in AMS starts 9:00 AM.
            // Arrival is after conference start, but transport was arranged — no missing travel.
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalDateTime confStart = SEP_16.atTime(9, 0);
            projector.handle(Stream.of(
                    stored(train("BRU", SEP_15.atTime(10, 0), LON, SEP_15.atTime(11, 56))),
                    stored(train(LON, SEP_16.atTime(7, 51), AMS, SEP_16.atTime(9, 36))),
                    stored(conferenceAt(AMS, confStart, SEP_17.atTime(17, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void noMissingTravelFromConferenceWhenLegDepartsConferenceCityBeforeConferenceEnds() {
            // Conference ends AMS 17:00; train from AMS departs 16:30 (before conference ends);
            // flight from LON departs next day. Transport from conference city was arranged.
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conferenceAt(AMS, SEP_16.atTime(9, 0), SEP_17.atTime(17, 0))),
                    stored(train(AMS, SEP_17.atTime(16, 30), LON, SEP_17.atTime(19, 0))),
                    stored(flight(LON, SEP_18.atTime(10, 0), PRG, SEP_18.atTime(12, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void noMissingTravelWhenConferenceHasNoSurroundingLegs() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conference("Steventon", SEP_16, SEP_17))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .isEmpty();
        }

        @Test
        void bothGapsReportedWhenConferenceCityDiffersFromBothArrivalAndDepartureCities() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalDateTime trainArrival = SEP_15.atTime(11, 57);
            LocalDateTime flightDeparture = SEP_18.atTime(10, 0);
            projector.handle(Stream.of(
                    stored(train("BRU", SEP_15.atTime(10, 0), LON, trainArrival)),
                    stored(conference("Steventon", SEP_16, SEP_17)),
                    stored(flight(LON, flightDeparture, AMS, SEP_18.atTime(12, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .containsExactlyInAnyOrder(
                            new ScheduleProblem.MissingTravel(LON, trainArrival, "Steventon", SEP_16.atStartOfDay()),
                            new ScheduleProblem.MissingTravel("Steventon", SEP_17.atStartOfDay(), LON, flightDeparture));
        }
    }

    // -------------------------------------------------------------------------
    // Deduplication of duplicate MissingTravel problems
    // -------------------------------------------------------------------------

    @Nested
    class MissingTravelDeduplication {

        @Test
        void sameGapDetectedByBothLegAndConferenceDetectionProducesOneProblem() {
            // Leg arrives LON; conference in AMS starts Sep 16; leg departs AMS Sep 18.
            // leg-to-leg detects: LON→AMS, arrives Sep 15, next leg Sep 18
            // conference detects: LON→AMS, arrives Sep 15, conference start Sep 16
            // → should produce exactly ONE problem with earliest nextDepartureAt
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalDateTime arrival = SEP_15.atTime(19, 18);
            LocalDateTime confStart = SEP_16.atStartOfDay();
            LocalDateTime legDep = SEP_18.atTime(11, 0);
            projector.handle(Stream.of(
                    stored(train("BRU", SEP_15.atTime(10, 0), LON, arrival)),
                    stored(conference(AMS, SEP_16, SEP_17)),
                    stored(flight(AMS, legDep, PRG, SEP_18.atTime(13, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .containsExactly(new ScheduleProblem.MissingTravel(
                            LON, arrival, AMS, confStart));
        }

        @Test
        void multipleConferenceEndsInSameCityWithSameNextDepartureProduceOneProblem() {
            // Two conferences end in LON at different times; both detect same Brussels departure.
            // conference-from detects: LON→AMS, arrives Sep 15 9:36, next leg Sep 18
            // conference-from detects: LON→AMS, arrives Sep 16 17:00, next leg Sep 18
            // → should produce exactly ONE problem with latest arrivedAt (Sep 16 17:00)
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            LocalDateTime end1 = SEP_15.atTime(9, 36);
            LocalDateTime end2 = SEP_16.atTime(17, 0);
            LocalDateTime nextDep = SEP_18.atTime(10, 56);
            projector.handle(Stream.of(
                    stored(conferenceAt(LON, SEP_14.atTime(10, 0), end1)),
                    stored(conferenceAt(LON, SEP_15.atTime(14, 0), end2)),
                    stored(flight(AMS, nextDep, PRG, SEP_18.atTime(13, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .containsExactly(new ScheduleProblem.MissingTravel(
                            LON, end2, AMS, nextDep));
        }
    }

    // -------------------------------------------------------------------------
    // Integration
    // -------------------------------------------------------------------------

    @Nested
    class FullItinerary {

        @Test
        void completeItineraryGeneratesNoProblems() {
            // SFO → London (LHR), hotel 3 nights, train to Amsterdam, hotel 2 nights
            ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());
            projector.handle(Stream.of(
                    stored(new FlightBooked(FlightId.random(), "BA", "BA286",
                            AirportCode.of("SFO"), SEP_15.atTime(11, 0),
                            AirportCode.of("LHR"), SEP_16.atTime(7, 0))),
                    stored(hotel("London", SEP_16, SEP_19)),
                    stored(train("London", SEP_19.atTime(9, 0), "Amsterdam", SEP_19.atTime(13, 0))),
                    stored(hotel("Amsterdam", SEP_19, SEP_21))));

            assertThat(projector.problems()).isEmpty();
        }

        @Test
        void missingTravelReportedWhenLegsArriveAndDepartFromDifferentCities() {
            ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());
            // Flight lands in London; next leg departs Amsterdam — missing travel detected.
            // London has no booked departure so hotel need cannot be determined.
            projector.handle(Stream.of(
                    stored(new FlightBooked(FlightId.random(), "BA", "BA286",
                            AirportCode.of("SFO"), SEP_15.atTime(11, 0),
                            AirportCode.of("LHR"), SEP_16.atTime(7, 0))),
                    stored(new FlightBooked(FlightId.random(), "KL", "KL1000",
                            AirportCode.of("AMS"), SEP_19.atTime(10, 0),
                            AirportCode.of("SFO"), SEP_19.atTime(13, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.MissingTravel)
                    .hasSize(1)
                    .first()
                    .isEqualTo(new ScheduleProblem.MissingTravel(
                            "London", SEP_16.atTime(7, 0), "Amsterdam", SEP_19.atTime(10, 0)));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
    private static final LocalDate SEP_19 = LocalDate.of(2026, 9, 19);
    private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);
    private static final LocalDate SEP_21 = LocalDate.of(2026, 9, 21);

    private static FlightBooked flight(String fromCode, LocalDateTime dep, String toCode, LocalDateTime arr) {
        return new FlightBooked(FlightId.random(), "Airline", "F1",
                AirportCode.of(fromCode), dep, AirportCode.of(toCode), arr);
    }

    private static TrainBooked train(String fromCity, LocalDateTime dep, String toCity, LocalDateTime arr) {
        return new TrainBooked(TrainTripId.random(),
                new TrainStationAddress("Station", fromCity, "XX", ""), dep,
                new TrainStationAddress("Station", toCity, "XX", ""), arr, "");
    }

    private static HotelBooked hotel(String city, LocalDate checkIn, LocalDate checkOut) {
        return new HotelBooked(HotelBookingId.random(), "Hotel",
                new Address("1 Street", city, "", "00000", "XX", null),
                checkIn.atTime(15, 0), checkOut.atTime(11, 0), BookingIntent.FINAL, null);
    }

    private static ConferenceTentativelyPlanned conference(String city, LocalDate start, LocalDate end) {
        return new ConferenceTentativelyPlanned(ConferenceId.random(), "Conf",
                start.atStartOfDay(), end.atStartOfDay(), "Venue",
                new Address("1 Street", city, "", "00000", "XX", null));
    }

    private static ConferenceTentativelyPlanned conferenceAt(String city, LocalDateTime start, LocalDateTime end) {
        return new ConferenceTentativelyPlanned(ConferenceId.random(), "Conf",
                start, end, "Venue",
                new Address("1 Street", city, "", "00000", "XX", null));
    }

    private static GatheringPlanned gathering(String title, LocalDate date, LocalTime start, LocalTime end) {
        return new GatheringPlanned(GatheringId.random(), title, "Venue",
                new Address("1 Street", "London", "", "EC1A", "GB", null),
                date, start, end, false, "");
    }

    // -------------------------------------------------------------------------
    // Gathering vs. conference different-city conflicts
    // -------------------------------------------------------------------------

    @Nested
    class DifferentCityConflictDetection {

        @Test
        void noConflictWhenGatheringCityMatchesConferenceCity() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conference("Amsterdam", SEP_15, SEP_17)),
                    stored(gatheringIn("Amsterdam", "AMS JUG", SEP_15,
                            LocalTime.of(18, 0), LocalTime.of(21, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                    .isEmpty();
        }

        @Test
        void conflictReportedWhenGatheringCityDiffersFromConferenceCity() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conference("Amsterdam", SEP_15, SEP_17)),
                    stored(gatheringIn("Brussels", "BRU JUG", SEP_16,
                            LocalTime.of(18, 0), LocalTime.of(21, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                    .hasSize(1);
            ScheduleProblem.DifferentCityConflict conflict = projector.problems().stream()
                    .filter(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                    .map(p -> (ScheduleProblem.DifferentCityConflict) p)
                    .findFirst().orElseThrow();
            assertThat(conflict.gatheringName()).isEqualTo("BRU JUG");
            assertThat(conflict.gatheringCity()).isEqualTo("Brussels");
            assertThat(conflict.conferenceName()).isEqualTo("Conf");
            assertThat(conflict.conferenceCity()).isEqualTo("Amsterdam");
            assertThat(conflict.date()).isEqualTo(SEP_16);
        }

        @Test
        void noConflictWhenGatheringIsOutsideConferenceDateRange() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conference("Amsterdam", SEP_15, SEP_17)),
                    stored(gatheringIn("Brussels", "BRU JUG", SEP_18,
                            LocalTime.of(18, 0), LocalTime.of(21, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                    .isEmpty();
        }

        @Test
        void noConflictWhenNoConferencePlanned() {
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(gatheringIn("Brussels", "BRU JUG", SEP_15,
                            LocalTime.of(18, 0), LocalTime.of(21, 0)))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                    .isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Clearing different-city conflicts
    // -------------------------------------------------------------------------

    @Nested
    class DifferentCityConflictClearedHandling {

        @Test
        void clearedConflictNoLongerAppears() {
            GatheringId gatheringId = GatheringId.random();
            ConferenceId conferenceId = ConferenceId.random();
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conferenceWithId(conferenceId, "Amsterdam", SEP_15, SEP_17)),
                    stored(gatheringWithId(gatheringId, "Brussels", "BRU JUG", SEP_16,
                            LocalTime.of(18, 0), LocalTime.of(21, 0))),
                    stored(new DifferentCityConflictCleared(gatheringId, conferenceId, "attending virtually"))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                    .isEmpty();
        }

        @Test
        void otherConflictsStillAppearAfterUnrelatedClear() {
            GatheringId gatheringId1 = GatheringId.random();
            GatheringId gatheringId2 = GatheringId.random();
            ConferenceId conferenceId = ConferenceId.random();
            ScheduleGapProjector projector = new ScheduleGapProjector(IDENTITY);
            projector.handle(Stream.of(
                    stored(conferenceWithId(conferenceId, "Amsterdam", SEP_15, SEP_17)),
                    stored(gatheringWithId(gatheringId1, "Brussels", "BRU JUG", SEP_16,
                            LocalTime.of(18, 0), LocalTime.of(21, 0))),
                    stored(gatheringWithId(gatheringId2, "London", "LJC", SEP_16,
                            LocalTime.of(18, 0), LocalTime.of(21, 0))),
                    stored(new DifferentCityConflictCleared(gatheringId1, conferenceId, ""))));

            assertThat(projector.problems())
                    .filteredOn(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                    .hasSize(1)
                    .extracting(p -> ((ScheduleProblem.DifferentCityConflict) p).gatheringName())
                    .containsExactly("LJC");
        }
    }

    private static ConferenceTentativelyPlanned conferenceWithId(ConferenceId id, String city,
                                                                  LocalDate start, LocalDate end) {
        return new ConferenceTentativelyPlanned(id, "Conf",
                start.atStartOfDay(), end.atStartOfDay(), "Venue",
                new Address("1 Street", city, "", "00000", "XX", null));
    }

    private static GatheringPlanned gatheringWithId(GatheringId id, String city, String title,
                                                    LocalDate date, LocalTime start, LocalTime end) {
        return new GatheringPlanned(id, title, "Venue",
                new Address("1 Street", city, "", "", "", null),
                date, start, end, false, "");
    }

    private static GatheringPlanned gatheringIn(String city, String title, LocalDate date, LocalTime start, LocalTime end) {
        return new GatheringPlanned(GatheringId.random(), title, "Venue",
                new Address("1 Street", city, "", "", "", null),
                date, start, end, false, "");
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
