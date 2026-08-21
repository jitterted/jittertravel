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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one mapping both views read. Every case asserts the <strong>whole href</strong>: a fix link
 * that is subtly wrong lands on a form that quietly ignores the parameter it could not parse, and
 * nothing anywhere says so.
 */
class ProblemFixTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @Test
    void aMissingHotelOffersBookingOneForItsCityAndItsExactNights() {
        List<ProblemFix> fixes = ProblemFix.forProblem(new ScheduleProblem.MissingHotel(
                "Johannesberg", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), "JCON"));

        assertThat(fixes).containsExactly(new ProblemFix("Book hotel",
                "/book-hotel?city=Johannesberg&checkIn=2026-09-10&checkOut=2026-09-14"));
    }

    @Test
    void aCityWithASpaceIsPercentEncodedSoTheFormReceivesItWhole() {
        List<ProblemFix> fixes = ProblemFix.forProblem(new ScheduleProblem.MissingHotel(
                "Lone Tree", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18), ""));

        assertThat(fixes.getFirst().href())
                .isEqualTo("/book-hotel?city=Lone+Tree&checkIn=2026-09-14&checkOut=2026-09-18");
    }

    /**
     * Three answers, in the order Ted would try them: flight is the common case in his data, train
     * is a real answer for a Frankfurt→Leipzig gap, and a ground transfer covers the short hop that
     * has no booking at all.
     */
    @Test
    void aMissingTravelGapOffersFlightThenTrainThenGroundTransfer() {
        List<ProblemFix> fixes = ProblemFix.forProblem(missingTravel());

        assertThat(fixes).containsExactly(
                new ProblemFix("Book flight",
                        "/book-flight?fromCity=Denver&toCity=Lone+Tree&date=2026-09-15"),
                new ProblemFix("Book train",
                        "/book-train?fromCity=Denver&toCity=Lone+Tree&date=2026-09-15"),
                new ProblemFix("Ground transfer",
                        "/plan-ground-transfer?date=2026-09-15"));
    }

    /**
     * The transfer form takes no typed cities — each end is a select of flight legs and booked
     * hotels — so its link carries the date alone. Preselecting an end is deliberately not
     * attempted: one {@code airport:} value can belong to several legs, so it would silently pick
     * a trip (docs/archived/GroundTransferPlan.md D13).
     */
    @Test
    void theGroundTransferFixCarriesOnlyTheDateNeverCities() {
        ProblemFix transfer = ProblemFix.forProblem(missingTravel()).getLast();

        assertThat(transfer.href())
                .doesNotContain("fromCity")
                .doesNotContain("toCity")
                .doesNotContain("airport:");
    }

    /**
     * The date is the day the traveller has to have moved <em>by</em>, read in the departure's own
     * zone — the two ends of a gap are usually in different zones, and a Tokyo departure is a day
     * ahead of the Denver arrival that stranded him.
     */
    @Test
    void theTravelDateIsTheDepartureDayInTheDeparturesOwnZone() {
        ScheduleProblem.MissingTravel crossesTheDateLine = new ScheduleProblem.MissingTravel(
                "San Francisco",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 18, 0),
                        ZoneId.of("America/Los_Angeles")),
                "Tokyo",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 16, 9, 0), TOKYO));

        assertThat(ProblemFix.forProblem(crossesTheDateLine).getFirst().href())
                .endsWith("&date=2026-09-16");
    }

    /**
     * One link per stay, never one "cancel the redundant one": which room to keep is Ted's call.
     * The target is the existing gated cancel page, so the link navigates and the report never POSTs.
     */
    @Test
    void aDuplicateHotelOffersOneCancelLinkPerStayNamingEachHotel() {
        HotelBookingId reichshof = HotelBookingId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        HotelBookingId parkHotel = HotelBookingId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        List<ProblemFix> fixes = ProblemFix.forProblem(new ScheduleProblem.DuplicateHotel(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 28),
                List.of(new ScheduleProblem.DuplicateStay(reichshof, "Reichshof", "Hamburg", BookingIntent.FINAL),
                        new ScheduleProblem.DuplicateStay(parkHotel, "Park Hotel", "Soltau", BookingIntent.TENTATIVE))));

        assertThat(fixes).containsExactly(
                new ProblemFix("Cancel \"Reichshof\"",
                        "/booked-hotels/11111111-1111-1111-1111-111111111111/cancel"),
                new ProblemFix("Cancel \"Park Hotel\"",
                        "/booked-hotels/22222222-2222-2222-2222-222222222222/cancel"));
    }

    @Test
    void aCityConflictOffersTheExistingClearConflictUrlUnchanged() {
        GatheringId gathering = GatheringId.of(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        ConferenceId conference = ConferenceId.of(UUID.fromString("44444444-4444-4444-4444-444444444444"));

        List<ProblemFix> fixes = ProblemFix.forProblem(new ScheduleProblem.DifferentCityConflict(
                "Aachen JUG", "Aachen", "DDD Europe", "Antwerp",
                LocalDate.of(2026, 6, 11), gathering, conference));

        assertThat(fixes).containsExactly(new ProblemFix("Clear this conflict",
                "/clear-conflict"
                + "?gatheringId=33333333-3333-3333-3333-333333333333"
                + "&conferenceId=44444444-4444-4444-4444-444444444444"
                + "&gatheringName=Aachen+JUG"
                + "&gatheringCity=Aachen"
                + "&conferenceName=DDD+Europe"
                + "&conferenceCity=Antwerp"
                + "&date=2026-06-11"));
    }

    /**
     * F6: its two sides are names, cities and times with no ids, so there is nothing to link to.
     * Empty here, and the renderers turn that into a greyed control with the reason — never a
     * silently missing one.
     */
    @Test
    void aSchedulingConflictHasNoFixYet() {
        ScheduleProblem.SchedulingConflict conflict = new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("Aachen JUG", "Aachen",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 8, 19, 0), BERLIN),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 8, 22, 0), BERLIN)),
                new ScheduleProblem.ConflictingGathering("Tokyo JUG", "Tokyo",
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 9, 10, 0), TOKYO),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 9, 12, 0), TOKYO)));

        assertThat(ProblemFix.forProblem(conflict)).isEmpty();
    }

    private static ScheduleProblem.MissingTravel missingTravel() {
        return new ScheduleProblem.MissingTravel(
                "Denver",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 11, 30),
                        ZoneId.of("America/Denver")),
                "Lone Tree",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 15, 9, 0),
                        ZoneId.of("America/Denver")));
    }
}
