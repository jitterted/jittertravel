package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemBandTest {

    @Test
    void missingHotelBecomesBedBandEndingOnTheLastNight() {
        // Check in on the 3rd, out on the 6th: three uncovered nights (3rd, 4th, 5th). The 6th is
        // a day the traveller has already left, so painting it would claim a bed was needed then.
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "London", LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6), "");

        Optional<ProblemBand> band = ProblemBand.from(problem);

        // Placement and words only: which fixes a band offers is ProblemFixTest's claim, and
        // asserting the whole record here would restate it in every placement test.
        assertThat(band).get()
                .extracting(ProblemBand::lane, ProblemBand::firstDay, ProblemBand::lastDay,
                            ProblemBand::title, ProblemBand::detail)
                .containsExactly(ProblemBand.Lane.BED,
                        LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 5),
                        "No hotel — London", "3 nights");
    }

    @Test
    void singleNightGapIsOneDayLongAndSaysNightSingular() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "Berlin", LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 4), "");

        Optional<ProblemBand> band = ProblemBand.from(problem);

        assertThat(band).get()
                .extracting(ProblemBand::firstDay, ProblemBand::lastDay, ProblemBand::detail)
                .containsExactly(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3), "1 night");
    }

    @Test
    void conferenceNameRidesAlongInTheDetail() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "Chicago", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16), "dev2next");

        Optional<ProblemBand> band = ProblemBand.from(problem);

        assertThat(band).get()
                .extracting(ProblemBand::detail)
                .isEqualTo("2 nights — dev2next");
    }

    @Test
    void missingTravelBecomesATravelBandFromArrivalToNextDeparture() {
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "London", ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 14, 30), ZoneId.of("Europe/London")),
                "Berlin", ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 9, 0), ZoneId.of("Europe/Berlin")));

        Optional<ProblemBand> band = ProblemBand.from(problem);

        assertThat(band).get()
                .extracting(ProblemBand::lane, ProblemBand::firstDay, ProblemBand::lastDay,
                            ProblemBand::title, ProblemBand::detail)
                .containsExactly(ProblemBand.Lane.TRAVEL,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                        "No travel — London → Berlin",
                        "Arrive 2:30 PM BST · depart 9:00 AM CEST");
    }

    @Test
    void bothEndsOfATravelGapNameTheirOwnZone() {
        // The two ends are eight hours apart in wall-clock terms and one hour apart in real time;
        // without the zone names the detail line invites the reader to subtract the wrong numbers.
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "Tokyo", ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 9, 0), ZoneId.of("Asia/Tokyo")),
                "Seoul", ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 10, 0), ZoneId.of("Asia/Seoul")));

        assertThat(ProblemBand.from(problem)).get()
                .extracting(ProblemBand::detail)
                .isEqualTo("Arrive 9:00 AM JST · depart 10:00 AM KST");
    }

    @Test
    void aTravelGapWhoseLocalDatesRunBackwardsStillCoversBothDays() {
        // Tokyo morning of the 3rd is the 2nd in UTC; the San Francisco evening departure that
        // follows it is local the 2nd. Placed arrival-to-departure the band would be empty, so it
        // covers the span of both ends instead.
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "Tokyo", ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 9, 0), ZoneId.of("Asia/Tokyo")),
                "San Francisco", ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 2, 20, 0), ZoneId.of("America/Los_Angeles")));

        assertThat(ProblemBand.from(problem)).get()
                .extracting(ProblemBand::firstDay, ProblemBand::lastDay)
                .containsExactly(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3));
    }

    @Test
    void aGapOutOfHomeIsOneDayOfBandAndNamesItsTimeOnce() {
        ZonedTimestamp needed = ZonedTimestamp.fromLocal(
                LocalDateTime.of(2026, 11, 11, 9, 0), ZoneId.of("Europe/Amsterdam"));
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "San Francisco", needed, "Ede", needed);

        assertThat(ProblemBand.from(problem)).get()
                .extracting(ProblemBand::firstDay, ProblemBand::lastDay, ProblemBand::detail)
                .containsExactly(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 11),
                                 "Nothing booked · needed by 9:00 AM CET");
    }

    @Test
    void duplicateHotelBandCoversTheDoublyBookedNightsExactly() {
        // Unlike a missing stay, this problem is already expressed in nights, so there is no
        // checkout day to trim: the 8th through the 10th is three nights and three days of band.
        ScheduleProblem problem = new ScheduleProblem.DuplicateHotel(
                LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 10),
                List.of(new ScheduleProblem.DuplicateStay(
                                HotelBookingId.random(), "Oak House", "Toronto", BookingIntent.FINAL),
                        new ScheduleProblem.DuplicateStay(
                                HotelBookingId.random(), "Doubletree", "Toronto", BookingIntent.TENTATIVE)));

        assertThat(ProblemBand.from(problem)).get()
                .extracting(ProblemBand::lane, ProblemBand::firstDay, ProblemBand::lastDay,
                            ProblemBand::title, ProblemBand::detail)
                .containsExactly(ProblemBand.Lane.DUPLICATE,
                        LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 10),
                        "2 hotels — Oak House · Doubletree", "3 nights booked twice");
    }

    @Test
    void conflictsHaveNoBandYet() {
        // Slice 3 gives both the CLASH lane.
        ZonedTimestamp start = ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 9, 0), ZoneId.of("Europe/London"));
        ZonedTimestamp end = ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 17, 0), ZoneId.of("Europe/London"));
        ScheduleProblem overlap = new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("XP Day", "London", start, end),
                new ScheduleProblem.ConflictingGathering("Lunch", "London", start, end));
        ScheduleProblem differentCity = new ScheduleProblem.DifferentCityConflict(
                "Lunch", "London", "dev2next", "Chicago", LocalDate.of(2026, 7, 1),
                GatheringId.random(), ConferenceId.random());

        assertThat(ProblemBand.from(overlap)).isEmpty();
        assertThat(ProblemBand.from(differentCity)).isEmpty();
    }
    /**
     * The band and the card read the same mapping, so the two views cannot offer different answers
     * to the same problem — the whole reason {@code ProblemFix.forProblem} exists.
     */
    @Test
    void aBandCarriesTheSameFixesTheListCardWouldOffer() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "London", LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6), "");

        assertThat(ProblemBand.from(problem)).get()
                .extracting(ProblemBand::fixes)
                .isEqualTo(ProblemFix.forProblem(problem));
    }

    @Test
    void aSchedulingClashIsNotAnAnchorSoItReachesNoBandAtAll() {
        // F6: nothing to link to, and unlike a card there is no slot vocabulary to keep on the
        // calendar — it simply is not clickable.
        ScheduleProblem problem = new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("A", "Aachen",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 19, 0), ZoneId.of("Europe/Berlin")),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 22, 0), ZoneId.of("Europe/Berlin"))),
                new ScheduleProblem.ConflictingGathering("B", "Bonn",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 20, 0), ZoneId.of("Europe/Berlin")),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 23, 0), ZoneId.of("Europe/Berlin"))));

        assertThat(ProblemBand.from(problem)).isEmpty();
    }

}
