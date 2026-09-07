package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.application.TravelLeg;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reference a fix link carries back to the problem it fixes.
 * <p>
 * Every case names the <strong>whole key</strong>. The format is what both ends agree on — the link
 * writes it, {@link ProblemContextLookup} matches on it — so a key that changes shape on one side
 * silently stops matching, and the banner quietly disappears rather than failing.
 */
class ProblemKeyTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @Test
    void aMissingHotelIsNamedByItsCityAndItsNights() {
        assertThat(ProblemKey.of(missingHotel("Denver", 14, 18)).value())
                .isEqualTo("hotel|Denver|2026-09-14|2026-09-18");
    }

    /**
     * The conference is context, not identity: the same city over the same nights is the same
     * missing bed whether or not a conference explains it.
     */
    @Test
    void aMissingHotelKeyIgnoresTheConferenceThatExplainsIt() {
        ScheduleProblem.MissingHotel withConference = new ScheduleProblem.MissingHotel(
                "Denver", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18), "dev2next");

        assertThat(ProblemKey.of(withConference).value())
                .isEqualTo(ProblemKey.of(missingHotel("Denver", 14, 18)).value());
    }

    /**
     * Instants, not local dates: the two ends of a gap sit in different zones, and two gaps that
     * read as the same local day are different gaps.
     */
    @Test
    void aMissingTravelGapIsNamedByBothCitiesAndBothInstants() {
        ScheduleProblem.MissingTravel gap = new ScheduleProblem.MissingTravel(
                "Denver", zoned(2026, 9, 14, 11, 30, DENVER),
                "Tokyo", zoned(2026, 9, 16, 9, 0, TOKYO));

        assertThat(ProblemKey.of(gap).value())
                .isEqualTo("travel|Denver|2026-09-14T17:30:00Z|Tokyo|2026-09-16T00:00:00Z");
    }

    /**
     * The nights alone would collide: two cities double-booked over the same run are two problems,
     * and one fix link must not explain the other.
     */
    @Test
    void aDuplicateHotelIsNamedByItsNightsAndTheStaysThatOverlap() {
        HotelBookingId reichshof = HotelBookingId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        HotelBookingId parkHotel = HotelBookingId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        ScheduleProblem.DuplicateHotel duplicate = new ScheduleProblem.DuplicateHotel(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 28),
                List.of(new ScheduleProblem.DuplicateStay(reichshof, "Reichshof", "Hamburg", BookingIntent.FINAL),
                        new ScheduleProblem.DuplicateStay(parkHotel, "Park Hotel", "Soltau", BookingIntent.TENTATIVE)));

        assertThat(ProblemKey.of(duplicate).value())
                .isEqualTo("dup|2026-08-26|2026-08-28"
                           + "|11111111-1111-1111-1111-111111111111,22222222-2222-2222-2222-222222222222");
    }

    @Test
    void aDifferentCityConflictIsNamedByItsTwoIdsAndItsDate() {
        GatheringId gathering = GatheringId.of(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        ConferenceId conference = ConferenceId.of(UUID.fromString("44444444-4444-4444-4444-444444444444"));

        ScheduleProblem.DifferentCityConflict conflict = new ScheduleProblem.DifferentCityConflict(
                "Aachen JUG", "Aachen", "DDD Europe", "Antwerp",
                LocalDate.of(2026, 6, 11), gathering, conference);

        assertThat(ProblemKey.of(conflict).value())
                .isEqualTo("city|33333333-3333-3333-3333-333333333333"
                           + "|44444444-4444-4444-4444-444444444444|2026-06-11");
    }

    /**
     * No fix link reaches a scheduling clash today — its sides carry no ids — but the switch is
     * exhaustive, so it has a key, and a future fix link for it will use exactly this one.
     */
    @Test
    void aSchedulingConflictIsNamedByBothSidesAndWhenTheyStart() {
        assertThat(ProblemKey.of(schedulingConflict()).value())
                .isEqualTo("clash|Aachen JUG|2026-09-08T17:00:00Z|Tokyo JUG|2026-09-09T01:00:00Z");
    }

    @Test
    void twoProblemsOfTheSameKindGetDifferentKeys() {
        assertThat(ProblemKey.of(missingHotel("Denver", 14, 18)).value())
                .isNotEqualTo(ProblemKey.of(missingHotel("Denver", 19, 21)).value());
        assertThat(ProblemKey.of(missingHotel("Denver", 14, 18)).value())
                .isNotEqualTo(ProblemKey.of(missingHotel("Lone Tree", 14, 18)).value());
    }

    @Test
    void aReferenceMatchesTheProblemItNamesAndNoOther() {
        ProblemKey ref = ProblemKey.of(missingHotel("Denver", 14, 18));

        assertThat(ref.matches(missingHotel("Denver", 14, 18)))
                .as("the same problem, recomputed from the same events, is still the same problem")
                .isTrue();
        assertThat(ref.matches(missingHotel("Denver", 19, 21))
                   || ref.matches(schedulingConflict()))
                .as("a different problem must never be explained by this reference")
                .isFalse();
    }

    private static ScheduleProblem.MissingHotel missingHotel(String city, int checkIn, int checkOut) {
        return new ScheduleProblem.MissingHotel(city,
                LocalDate.of(2026, 9, checkIn), LocalDate.of(2026, 9, checkOut), "");
    }

    private static ScheduleProblem.SchedulingConflict schedulingConflict() {
        return new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("Aachen JUG", "Aachen",
                        zoned(2026, 9, 8, 19, 0, ZoneId.of("Europe/Berlin")),
                        zoned(2026, 9, 8, 22, 0, ZoneId.of("Europe/Berlin"))),
                new ScheduleProblem.ConflictingGathering("Tokyo JUG", "Tokyo",
                        zoned(2026, 9, 9, 10, 0, TOKYO),
                        zoned(2026, 9, 9, 12, 0, TOKYO)));
    }

    @Test
    void overlappingTravelIsKeyedByBothLegsIds() {
        TrainTripId early = TrainTripId.random();
        TrainTripId late = TrainTripId.random();
        ScheduleProblem overlap = overlap(early, late);

        assertThat(ProblemKey.of(overlap).value())
                .isEqualTo("legs|train:" + early.id() + "|train:" + late.id());
    }

    @Test
    void twoTransferOverlapsOverTheSameTrainDoNotCollide() {
        // The reason the key is the leg's id and not its page: every ground transfer's page is
        // /itinerary, so keying on the path made both of these "legs|/itinerary|/booked-trains/X"
        // and a fix link on the second opened the first one's banner and fixes.
        TrainTripId train = TrainTripId.random();

        assertThat(ProblemKey.of(transferOverlap(GroundTransferId.random(), train)).value())
                .isNotEqualTo(ProblemKey.of(transferOverlap(GroundTransferId.random(), train)).value());
    }

    @Test
    void twoTransfersOverlappingEachOtherDoNotCollideWithAnotherSuchPair() {
        assertThat(ProblemKey.of(transferPair()).value())
                .isNotEqualTo(ProblemKey.of(transferPair()).value());
    }

    @Test
    void twoOverlapsOverTheSameLegDoNotCollide() {
        // Three mutually overlapping legs produce three pairs, and each must key differently or a
        // fix link would resolve to the wrong pair's banner.
        TrainTripId a = TrainTripId.random();
        TrainTripId b = TrainTripId.random();
        TrainTripId c = TrainTripId.random();

        assertThat(ProblemKey.of(overlap(a, b)).value())
                .isNotEqualTo(ProblemKey.of(overlap(a, c)).value());
    }

    @Test
    void anOverlapKeyMatchesTheProblemItNames() {
        ScheduleProblem overlap = overlap(TrainTripId.random(), TrainTripId.random());

        assertThat(ProblemKey.of(overlap).matches(overlap))
                .isTrue();
    }

    private static ZonedTimestamp zoned(int year, int month, int day, int hour, int minute, ZoneId zone) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(year, month, day, hour, minute), zone);
    }

    private static ScheduleProblem.OverlappingTravel overlap(TrainTripId first, TrainTripId second) {
        return new ScheduleProblem.OverlappingTravel(
                overlapLeg(new TravelLeg.Train(first, "ICE 597"), 9),
                overlapLeg(new TravelLeg.Train(second, "ICE 597"), 10));
    }

    private static ScheduleProblem.OverlappingTravel transferOverlap(GroundTransferId transfer,
                                                                    TrainTripId train) {
        return new ScheduleProblem.OverlappingTravel(
                overlapLeg(new TravelLeg.Transfer(transfer, "Hotel → Hbf"), 9),
                overlapLeg(new TravelLeg.Train(train, "ICE 597"), 10));
    }

    private static ScheduleProblem.OverlappingTravel transferPair() {
        return new ScheduleProblem.OverlappingTravel(
                overlapLeg(new TravelLeg.Transfer(GroundTransferId.random(), "Hotel → Hbf"), 9),
                overlapLeg(new TravelLeg.Transfer(GroundTransferId.random(), "Hotel → Hbf"), 10));
    }

    private static ScheduleProblem.OverlappingLeg overlapLeg(TravelLeg leg, int hour) {
        return new ScheduleProblem.OverlappingLeg(leg, "Hamburg", "Berlin",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, hour, 0),
                        ZoneId.of("Europe/Berlin")),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, hour + 2, 0),
                        ZoneId.of("Europe/Berlin")));
    }

}
