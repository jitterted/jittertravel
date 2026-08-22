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

import static org.assertj.core.api.Assertions.assertThat;

class ProblemBandTest {

    @Test
    void missingHotelBecomesBedBandEndingOnTheLastNight() {
        // Check in on the 3rd, out on the 6th: three uncovered nights (3rd, 4th, 5th). The 6th is
        // a day the traveller has already left, so painting it would claim a bed was needed then.
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "London", LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6), "");

        ProblemBand band = ProblemBand.from(problem);

        // Placement and words only: which fixes a band offers is ProblemFixTest's claim, and
        // asserting the whole record here would restate it in every placement test.
        assertThat(band)
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

        ProblemBand band = ProblemBand.from(problem);

        assertThat(band)
                .extracting(ProblemBand::firstDay, ProblemBand::lastDay, ProblemBand::detail)
                .containsExactly(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3), "1 night");
    }

    @Test
    void conferenceNameRidesAlongInTheDetail() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "Chicago", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16), "dev2next");

        ProblemBand band = ProblemBand.from(problem);

        assertThat(band)
                .extracting(ProblemBand::detail)
                .isEqualTo("2 nights — dev2next");
    }

    @Test
    void missingTravelBecomesATravelBandFromArrivalToNextDeparture() {
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "London", ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 14, 30), ZoneId.of("Europe/London")),
                "Berlin", ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 9, 0), ZoneId.of("Europe/Berlin")));

        ProblemBand band = ProblemBand.from(problem);

        assertThat(band)
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

        assertThat(ProblemBand.from(problem))
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

        assertThat(ProblemBand.from(problem))
                .extracting(ProblemBand::firstDay, ProblemBand::lastDay)
                .containsExactly(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3));
    }

    @Test
    void aGapOutOfHomeIsOneDayOfBandAndNamesItsTimeOnce() {
        ZonedTimestamp needed = ZonedTimestamp.fromLocal(
                LocalDateTime.of(2026, 11, 11, 9, 0), ZoneId.of("Europe/Amsterdam"));
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "San Francisco", needed, "Ede", needed);

        assertThat(ProblemBand.from(problem))
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

        assertThat(ProblemBand.from(problem))
                .extracting(ProblemBand::lane, ProblemBand::firstDay, ProblemBand::lastDay,
                            ProblemBand::title, ProblemBand::detail)
                .containsExactly(ProblemBand.Lane.DUPLICATE,
                        LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 10),
                        "2 hotels — Oak House · Doubletree", "3 nights booked twice");
    }

    @Test
    void differentCityConflictBecomesAOneDayCityClashBand() {
        ScheduleProblem problem = new ScheduleProblem.DifferentCityConflict(
                "Lunch", "Denver", "dev2next", "Chicago", LocalDate.of(2026, 9, 16),
                GatheringId.random(), ConferenceId.random());

        assertThat(ProblemBand.from(problem))
                .extracting(ProblemBand::marker, ProblemBand::firstDay, ProblemBand::lastDay,
                            ProblemBand::title, ProblemBand::detail)
                .containsExactly(ProblemBand.Marker.CLASH_CITY,
                        LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 16),
                        "City clash — Lunch · dev2next", "Denver vs Chicago");
    }

    @Test
    void schedulingConflictBecomesAClashBandNamingEachSidesZone() {
        ScheduleProblem problem = new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("XP Day", "London",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 9, 0), ZoneId.of("Europe/London")),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 17, 0), ZoneId.of("Europe/London"))),
                new ScheduleProblem.ConflictingGathering("Lunch", "London",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 12, 0), ZoneId.of("Europe/London")),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 13, 0), ZoneId.of("Europe/London"))));

        assertThat(ProblemBand.from(problem))
                .extracting(ProblemBand::marker, ProblemBand::firstDay, ProblemBand::lastDay,
                            ProblemBand::title, ProblemBand::detail)
                .containsExactly(ProblemBand.Marker.CLASH_SCHEDULING,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1),
                        "Clash — XP Day · Lunch", "9:00 AM BST · 12:00 PM BST");
    }

    @Test
    void bothClashMarkersShareTheOneClashLane() {
        // One lane, two colours: a day with both kinds stacks them in the same block of sub-rows
        // rather than reserving a row for each kind on every week of the calendar.
        assertThat(ProblemBand.Marker.CLASH_CITY.lane())
                .isEqualTo(ProblemBand.Marker.CLASH_SCHEDULING.lane())
                .isEqualTo(ProblemBand.Lane.CLASH);
    }

    @Test
    void aSchedulingClashAcrossZonesSpansEveryLocalDateEitherSideTouches() {
        // The San Francisco evening of the 1st is the Tokyo morning of the 2nd. Placed from one
        // side's dates to the other's the band would run backwards and render as nothing.
        ScheduleProblem problem = new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("Tokyo standup", "Tokyo",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 2, 10, 0), ZoneId.of("Asia/Tokyo")),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 2, 11, 0), ZoneId.of("Asia/Tokyo"))),
                new ScheduleProblem.ConflictingGathering("SF dinner", "San Francisco",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 18, 0), ZoneId.of("America/Los_Angeles")),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 20, 0), ZoneId.of("America/Los_Angeles"))));

        assertThat(ProblemBand.from(problem))
                .extracting(ProblemBand::firstDay, ProblemBand::lastDay, ProblemBand::detail)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2),
                        "10:00 AM JST · 6:00 PM PDT");
    }
    /**
     * The band and the card read the same mapping, so the two views cannot offer different answers
     * to the same problem — the whole reason {@code ProblemFix.forProblem} exists.
     */
    @Test
    void aBandCarriesTheSameFixesTheListCardWouldOffer() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "London", LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6), "");

        assertThat(ProblemBand.from(problem))
                .extracting(ProblemBand::fixes)
                .isEqualTo(ProblemFix.forProblem(problem, FixOrigin.PROBLEM_CALENDAR));
    }

    @Test
    void aSchedulingClashBandOffersNoFixSoItIsNotAnAnchor() {
        // F6: nothing to link to, and unlike a card there is no slot vocabulary to keep on the
        // calendar — it simply is not clickable.
        ScheduleProblem problem = new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("A", "Aachen",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 19, 0), ZoneId.of("Europe/Berlin")),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 22, 0), ZoneId.of("Europe/Berlin"))),
                new ScheduleProblem.ConflictingGathering("B", "Bonn",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 20, 0), ZoneId.of("Europe/Berlin")),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 23, 0), ZoneId.of("Europe/Berlin"))));

        assertThat(ProblemBand.from(problem).fixes()).isEmpty();
    }

    /**
     * The city clash is the one clash that <em>can</em> be acted on, and the band offers exactly
     * what the card offers — the same "Clear this conflict" URL, from the one mapping.
     */
    @Test
    void aCityClashBandCarriesTheSameFixTheListCardWouldOffer() {
        ScheduleProblem problem = new ScheduleProblem.DifferentCityConflict(
                "Lunch", "Denver", "dev2next", "Chicago", LocalDate.of(2026, 9, 16),
                GatheringId.random(), ConferenceId.random());

        assertThat(ProblemBand.from(problem).fixes())
                .isEqualTo(ProblemFix.forProblem(problem, FixOrigin.PROBLEM_CALENDAR));
    }

}
