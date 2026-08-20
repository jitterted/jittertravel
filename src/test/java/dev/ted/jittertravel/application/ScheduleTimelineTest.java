package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The rules of the location trace, at unit level. The whole-itinerary claims live in
 * {@code ScheduleProblemsAcceptanceTest}; these pin the individual rules that make those come out
 * right, and would otherwise only be tested by luck.
 */
class ScheduleTimelineTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final HomeCities NO_HOME = new HomeCities(List.of());
    private static final HomeCities HOME_IN_SF = new HomeCities(List.of("San Francisco"));

    @Nested
    class WithinDayOrdering {

        @Test
        void aCheckInRecordedAtMidnightDoesNotOutrankTheFlightThatGetsHimThere() {
            // The hostile case for ordering by instant: a Leipzig check-in stamped 00:00 sorts
            // ahead of the 11:30 flight that carries him there, and the walk would report a gap
            // from London to Leipzig with a booked flight sitting right there. Ordering by day
            // and then by role — leave, travel, arrive — is what makes the clock time irrelevant.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(stay("Le Mirage", "London", "2026-06-21 15:00", "2026-06-22 11:00"),
                            stay("Staycity", "Leipzig", "2026-06-22 00:00", "2026-06-24 00:00")),
                    List.of(),
                    List.of(movement("London", "2026-06-22 11:30", "Frankfurt", "2026-06-22 14:00"),
                            movement("Frankfurt", "2026-06-22 16:00", "Leipzig", "2026-06-22 19:00")),
                    NO_HOME);

            assertThat(timeline.missingTravel()).isEmpty();
        }

        @Test
        void twoStaysHandingOverOnTheSameDayInDifferentCitiesIsAGap() {
            // The same shape with no leg between them: checking out of Hamburg and into Soltau on
            // the 26th is exactly one missing journey, and the day-ordering must still see it.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(stay("Reichshof", "Hamburg", "2026-08-25 15:00", "2026-08-26 11:00"),
                            stay("Park Hotel", "Soltau", "2026-08-26 15:00", "2026-08-31 11:00")),
                    List.of(), List.of(), NO_HOME);

            assertThat(timeline.missingTravel())
                    .extracting(ScheduleProblem.MissingTravel::fromCity,
                                ScheduleProblem.MissingTravel::toCity)
                    .containsExactly(tuple("Hamburg", "Soltau"));
        }
    }

    @Nested
    class NightsInTransit {

        @Test
        void aNightSpentEntirelyOnAFlightNeedsNoBed() {
            // "I am technically in SFO until I land in FRA" — the red-eye of the 6th lands on the
            // 7th, so the night of the 6th belongs to the leg, not to a hotel.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(stay("Lindner", "Cologne", "2026-06-07 15:00", "2026-06-08 11:00")),
                    List.of(),
                    List.of(movement("San Francisco", "2026-06-06 13:55", "Frankfurt", "2026-06-07 09:45"),
                            movement("Frankfurt", "2026-06-07 11:30", "Cologne", "2026-06-07 13:00")),
                    NO_HOME);

            assertThat(timeline.missingHotels()).isEmpty();
        }

        @Test
        void aNightBeforeADaytimeFlightStillNeedsABed() {
            // Same shape, but the leg lands the day it leaves — nothing covers the night before it.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(),
                    List.of(occupancy("JCrete", "Soltau", "2026-08-25 09:00", "2026-08-25 17:00")),
                    List.of(movement("Soltau", "2026-08-27 09:00", "Frankfurt", "2026-08-27 13:00")),
                    NO_HOME);

            assertThat(timeline.missingHotels())
                    .extracting(ScheduleProblem.MissingHotel::city,
                                ScheduleProblem.MissingHotel::checkIn,
                                ScheduleProblem.MissingHotel::checkOut)
                    .containsExactly(tuple("Soltau", date("2026-08-25"), date("2026-08-27")));
        }
    }

    @Nested
    class TripBreak {

        @Test
        void aFortnightOfSilenceEndsTheRunRatherThanBookingRoomsUntilTheNextTrip() {
            // Two conferences eleven months apart with nothing booked between them. Last-known-
            // location carried literally would demand a hotel in Oslo every night until December.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(),
                    List.of(occupancy("Oslo Conf", "Oslo", "2026-01-10 09:00", "2026-01-12 17:00"),
                            occupancy("Lima Conf", "Lima", "2026-12-10 09:00", "2026-12-12 17:00")),
                    List.of(), NO_HOME);

            assertThat(timeline.missingHotels())
                    .extracting(ScheduleProblem.MissingHotel::city,
                                ScheduleProblem.MissingHotel::checkIn,
                                ScheduleProblem.MissingHotel::checkOut)
                    .containsExactly(tuple("Oslo", date("2026-01-10"), date("2026-01-13")),
                                     tuple("Lima", date("2026-12-10"), date("2026-12-12")));
        }

        @Test
        void aQuietStretchInsideATripStillNeedsEveryNight() {
            // Six nights between two gatherings in the same city is a trip in progress, not a gap
            // between trips: every one of those nights needs a bed.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(),
                    List.of(occupancy("Rush", "New York", "2026-07-28 19:30", "2026-07-28 23:30"),
                            occupancy("Rush", "New York", "2026-08-03 18:30", "2026-08-03 23:30")),
                    List.of(movement("New York", "2026-08-04 14:55", "San Francisco", "2026-08-04 18:24")),
                    HOME_IN_SF);

            assertThat(timeline.missingHotels())
                    .extracting(ScheduleProblem.MissingHotel::city,
                                ScheduleProblem.MissingHotel::checkIn,
                                ScheduleProblem.MissingHotel::checkOut)
                    .containsExactly(tuple("New York", date("2026-07-28"), date("2026-08-04")));
        }
    }

    @Nested
    class DuplicateStays {

        @Test
        void twoStaysInDifferentCitiesOverlappingTheSameNightsAreStillDuplicates() {
            // He can only sleep in one of them, and the one in the other city is worse, not better.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(stay("Oak House", "Toronto", "2026-08-08 15:00", "2026-08-11 11:00"),
                            stay("Aparthotel", "Montreal", "2026-08-08 15:00", "2026-08-11 11:00")),
                    List.of(), List.of(), NO_HOME);

            assertThat(timeline.duplicateHotels())
                    .extracting(ScheduleProblem.DuplicateHotel::firstNight,
                                ScheduleProblem.DuplicateHotel::lastNight)
                    .containsExactly(tuple(date("2026-08-08"), date("2026-08-10")));
        }

        @Test
        void staysThatMeetOnCheckoutDayShareNoNightAndAreNotDuplicates() {
            // Two Antwerp hotels, the 10th to the 13th and the 13th to the 15th, are a hand-off.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(stay("Park Inn", "Antwerp", "2026-06-10 15:00", "2026-06-13 11:00"),
                            stay("Radisson", "Antwerp", "2026-06-13 15:00", "2026-06-15 11:00")),
                    List.of(), List.of(), NO_HOME);

            assertThat(timeline.duplicateHotels()).isEmpty();
        }

        @Test
        void aTentativeBookingBothCoversItsNightsAndDuplicates() {
            // "Tentative still means I booked the hotel with a provider, and until I cancel it,
            // that reservation exists." So intent is not a detection input — it only rides along
            // to say which of the two to cancel.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(stay("Oak House", "Toronto", "2026-08-08 15:00", "2026-08-10 11:00"),
                            stay("Doubletree", "Toronto", "2026-08-08 15:00", "2026-08-10 11:00",
                                    BookingIntent.TENTATIVE)),
                    List.of(), List.of(), NO_HOME);

            assertThat(timeline.missingHotels()).isEmpty();
            assertThat(timeline.duplicateHotels())
                    .singleElement()
                    .extracting(duplicate -> duplicate.stays().stream()
                            .map(ScheduleProblem.DuplicateStay::bookingIntent)
                            .toList())
                    .isEqualTo(List.of(BookingIntent.FINAL, BookingIntent.TENTATIVE));
        }

        @Test
        void aThirdBookingJoiningPartWayThroughIsItsOwnProblem() {
            // The run holds only while the same stays are the ones overlapping.
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(stay("Oak House", "Toronto", "2026-08-08 15:00", "2026-08-12 11:00"),
                            stay("Doubletree", "Toronto", "2026-08-08 15:00", "2026-08-12 11:00"),
                            stay("Aparthotel", "Toronto", "2026-08-10 15:00", "2026-08-12 11:00")),
                    List.of(), List.of(), NO_HOME);

            assertThat(timeline.duplicateHotels())
                    .extracting(ScheduleProblem.DuplicateHotel::firstNight,
                                ScheduleProblem.DuplicateHotel::lastNight,
                                duplicate -> duplicate.stays().size())
                    .containsExactly(tuple(date("2026-08-08"), date("2026-08-09"), 2),
                                     tuple(date("2026-08-10"), date("2026-08-11"), 3));
        }
    }

    @Nested
    class HomeNights {

        @Test
        void nightsAtHomeNeedNoBedAndDoNotSplitTheRunsAroundThem() {
            ScheduleTimeline timeline = new ScheduleTimeline(
                    List.of(),
                    List.of(occupancy("Rush", "New York", "2026-07-28 19:30", "2026-07-28 23:30")),
                    List.of(movement("San Francisco", "2026-07-27 08:55", "New York", "2026-07-27 17:45"),
                            movement("New York", "2026-07-30 14:55", "San Francisco", "2026-07-30 18:24"),
                            movement("San Francisco", "2026-08-08 08:10", "Toronto", "2026-08-08 16:10")),
                    HOME_IN_SF);

            assertThat(timeline.missingHotels())
                    .extracting(ScheduleProblem.MissingHotel::city,
                                ScheduleProblem.MissingHotel::checkIn,
                                ScheduleProblem.MissingHotel::checkOut)
                    .containsExactly(tuple("New York", date("2026-07-27"), date("2026-07-30")));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ScheduleTimeline.Stay stay(String name, String city, String checkIn, String checkOut) {
        return stay(name, city, checkIn, checkOut, BookingIntent.FINAL);
    }

    private static ScheduleTimeline.Stay stay(String name, String city, String checkIn, String checkOut,
                                              BookingIntent intent) {
        return new ScheduleTimeline.Stay(HotelBookingId.random(), name, city,
                at(checkIn), at(checkOut), intent);
    }

    private static ScheduleTimeline.Occupancy occupancy(String name, String city, String start, String end) {
        return new ScheduleTimeline.Occupancy(name, city, at(start), at(end),
                ScheduleTimeline.Occupancy.Kind.CONFERENCE);
    }

    private static ScheduleTimeline.Movement movement(String fromCity, String departure,
                                                      String toCity, String arrival) {
        return new ScheduleTimeline.Movement(fromCity, at(departure), toCity, at(arrival));
    }

    private static ZonedTimestamp at(String dayAndTime) {
        return ZonedTimestamp.fromLocal(
                LocalDateTime.parse(dayAndTime.replace(' ', 'T')), ZONE);
    }

    private static LocalDate date(String isoDate) {
        return LocalDate.parse(isoDate);
    }
}
