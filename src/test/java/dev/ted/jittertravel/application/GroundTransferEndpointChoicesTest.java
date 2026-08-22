package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which endpoint a missing-travel gap can settle on its own.
 * <p>
 * The rule is <strong>exactly one candidate, or none</strong> (Ted, 2026-08-21). A wrong endpoint
 * is not a wasted click: it writes a transfer that removes the very gap it was entered to close,
 * and nothing afterwards says the schedule is still broken.
 */
class GroundTransferEndpointChoicesTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /**
     * Ted's own case (2026-08-21): stuck in Johannesberg until the 13th, due in Frankfurt the same
     * day. One stay checks out at one end and one checks in at the other, so the form can open on
     * both.
     */
    @Test
    void aGapWithOneCandidateAtEachEndSettlesBothOfThem() {
        GroundTransferEndpointChoices choices = new GroundTransferEndpointChoices(
                List.of(),
                List.of(),
                List.of(stay("hotel:seminarzentrum", "SeminarZentrum Rückersbach",
                        "Johannesberg", "2026-09-13", "11:00")),
                List.of(stay("hotel:holiday-inn", "Holiday Inn Frankfurt - Alte Oper",
                        "Frankfurt", "2026-09-13", "15:00")));

        assertThat(choices.originFor(johannesbergToFrankfurt()))
                .get()
                .extracting(TransferEndpointOption::token)
                .isEqualTo("hotel:seminarzentrum");
        assertThat(choices.destinationFor(johannesbergToFrankfurt()))
                .get()
                .extracting(TransferEndpointOption::token)
                .isEqualTo("hotel:holiday-inn");
    }

    /** The case the whole rule exists for. */
    @Test
    void twoCandidatesInTheGapsCitySettleNothing() {
        GroundTransferEndpointChoices choices = new GroundTransferEndpointChoices(
                List.of(), List.of(), List.of(),
                List.of(stay("hotel:holiday-inn", "Holiday Inn", "Frankfurt", "2026-09-13", "15:00"),
                        stay("hotel:hof", "Frankfurter Hof", "Frankfurt", "2026-09-13", "16:00")));

        assertThat(choices.destinationFor(johannesbergToFrankfurt()))
                .as("two hotels in one city that day: the form asks rather than guessing")
                .isEmpty();
    }

    @Test
    void anEndpointInAnotherCityIsNeverACandidate() {
        GroundTransferEndpointChoices choices = new GroundTransferEndpointChoices(
                List.of(), List.of(), List.of(),
                List.of(stay("hotel:munich", "Bayerischer Hof", "Munich", "2026-09-13", "15:00")));

        assertThat(choices.destinationFor(johannesbergToFrankfurt())).isEmpty();
    }

    /**
     * The gap runs from the arrival that stranded him to the departure he has to make, and an
     * endpoint outside those days belongs to a different journey.
     */
    @Test
    void anEndpointOutsideTheGapsDaysIsNeverACandidate() {
        GroundTransferEndpointChoices choices = new GroundTransferEndpointChoices(
                List.of(), List.of(), List.of(),
                List.of(stay("hotel:next-month", "Holiday Inn", "Frankfurt", "2026-10-13", "15:00")));

        assertThat(choices.destinationFor(johannesbergToFrankfurt())).isEmpty();
    }

    /** A gap the app holds no endpoint for at all — a train station, a conference venue. */
    @Test
    void nothingOfferedSettlesNothing() {
        GroundTransferEndpointChoices choices = new GroundTransferEndpointChoices(
                List.of(), List.of(), List.of(), List.of());

        assertThat(choices.originFor(johannesbergToFrankfurt())).isEmpty();
        assertThat(choices.destinationFor(johannesbergToFrankfurt())).isEmpty();
    }

    /**
     * Legs and stays are one pool per end: the airport you landed at and the hotel you are checking
     * out of are both places this hop can start from, and two of them is still ambiguous.
     */
    @Test
    void aFlightLegCountsAsACandidateAlongsideTheStays() {
        GroundTransferEndpointChoices onlyTheLeg = new GroundTransferEndpointChoices(
                List.of(leg("airport:FRA", "Frankfurt", "2026-09-13", "09:00")),
                List.of(), List.of(), List.of());
        // Each end pools only what can apply to it — the "To" select's legs are departures — so the
        // pair that competes here is a departure and a check-in.
        GroundTransferEndpointChoices theLegAndAStay = new GroundTransferEndpointChoices(
                List.of(),
                List.of(leg("airport:FRA", "Frankfurt", "2026-09-13", "18:00")),
                List.of(),
                List.of(stay("hotel:holiday-inn", "Holiday Inn", "Frankfurt", "2026-09-13", "15:00")));

        assertThat(onlyTheLeg.originFor(frankfurtToJohannesberg()))
                .get()
                .extracting(TransferEndpointOption::token)
                .isEqualTo("airport:FRA");
        assertThat(theLegAndAStay.destinationFor(johannesbergToFrankfurt()))
                .as("an airport and a hotel in one city that day is two answers, not one")
                .isEmpty();
    }

    /**
     * The stay's own matching location, not the city printed in its label: a gap says Johannesberg
     * where the hotel's address says Rückersbach, which is exactly what {@code locationForMatching}
     * is for.
     */
    @Test
    void theCandidateIsMatchedOnTheLocationTheScheduleUsesNotTheAddressCity() {
        TransferEndpointOption seminarZentrum = new TransferEndpointOption(
                "hotel:seminarzentrum",
                "SeminarZentrum Rückersbach — Rückersbach · check out Sun Sep 13, 11:00 AM",
                "Johannesberg", "2026-09-13", "11:00");
        GroundTransferEndpointChoices choices = new GroundTransferEndpointChoices(
                List.of(), List.of(), List.of(seminarZentrum), List.of());

        assertThat(choices.originFor(johannesbergToFrankfurt())).contains(seminarZentrum);
    }

    private static ScheduleProblem.MissingTravel johannesbergToFrankfurt() {
        return new ScheduleProblem.MissingTravel(
                "Johannesberg", at("2026-09-09T17:00"),
                "Frankfurt", at("2026-09-13T15:00"));
    }

    private static ScheduleProblem.MissingTravel frankfurtToJohannesberg() {
        return new ScheduleProblem.MissingTravel(
                "Frankfurt", at("2026-09-13T09:00"),
                "Johannesberg", at("2026-09-13T14:00"));
    }

    private static TransferEndpointOption stay(String token, String name, String city,
                                               String day, String time) {
        return new TransferEndpointOption(token, name + " — " + city, city, day, time);
    }

    private static TransferEndpointOption leg(String token, String city, String day, String time) {
        return new TransferEndpointOption(token, city + " leg", city, day, time);
    }

    private static ZonedTimestamp at(String local) {
        return ZonedTimestamp.fromLocal(LocalDateTime.parse(local), BERLIN);
    }
}
