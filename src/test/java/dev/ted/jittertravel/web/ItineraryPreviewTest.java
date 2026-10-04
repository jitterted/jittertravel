package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryEvaluation;
import dev.ted.jittertravel.application.PastedItinerary;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ChangeFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryContext;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.OverlapsAnotherItineraryLeg;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.domain.ScheduledLegs;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ItineraryPreviewTest {

    private static final FlightId BLOCKING = FlightId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private static final ScheduledLeg BLOCKING_LEG = new ScheduledLeg(new ScheduledLegId.Flight(BLOCKING),
            ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 9, 0), ZoneId.of("America/Chicago")),
            ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 11, 0), ZoneId.of("America/Chicago")));

    static Stream<Arguments> eachRefusalAsTheRowSaysIt() {
        return Stream.of(
                arguments(new DepartureNotInFuture("x"), "Already departed"),
                arguments(new InvalidDateRange("x"), "Arrives before it departs"),
                arguments(new OverlapsAnotherItineraryLeg(3), "Overlaps flight 3 of this itinerary"),
                arguments(new OverlappingLegRefused(BLOCKING_LEG), "Overlaps a flight departing Oct 18, 9:00 AM"));
    }

    @ParameterizedTest
    @MethodSource
    void eachRefusalAsTheRowSaysIt(RuntimeException refusal, String words) {
        ItineraryPreview preview = ItineraryPreview.from(evaluation(refusal));

        assertThat(preview.rows())
                .singleElement()
                .extracting(ItineraryPreview.Row::problem)
                .isEqualTo(words);
        assertThat(preview.problemSummary())
                .as("a refused leg counts, in the shared sentence")
                .isEqualTo("1 problem to fix below.");
    }

    @Test
    void aCollisionWithAFlightAlreadyBookedLinksToIt() {
        ItineraryPreview preview = ItineraryPreview.from(evaluation(new OverlappingLegRefused(BLOCKING_LEG)));

        ItineraryPreview.Row row = preview.rows().getFirst();
        assertThat(row.linkPath())
                .isEqualTo("/booked-flights/" + BLOCKING.id());
        assertThat(row.linkLabel())
                .isEqualTo("Open that flight");
    }

    @Test
    void aRowLinksOnlyWhenItCollidesWithABookedFlight() {
        ItineraryPreview.Row clean = ItineraryPreview.from(evaluation(null)).rows().getFirst();
        ItineraryPreview.Row departed = ItineraryPreview.from(evaluation(new DepartureNotInFuture("x"))).rows().getFirst();
        ItineraryPreview.Row collides = ItineraryPreview.from(evaluation(new OverlappingLegRefused(BLOCKING_LEG))).rows().getFirst();

        assertThat(clean.hasLink())
                .as("clean leg")
                .isFalse();
        assertThat(departed.hasLink())
                .as("refused, but with nothing to open")
                .isFalse();
        assertThat(collides.hasLink())
                .as("collides with a booked flight")
                .isTrue();
    }

    @Test
    void itCountsTheLegsItShows() {
        assertThat(ItineraryPreview.from(evaluation(null)).legCount())
                .isEqualTo(1);
    }

    @Test
    void aCleanLegSaysNothingAndIsNotCounted() {
        ItineraryPreview preview = ItineraryPreview.from(evaluation(null));

        assertThat(preview.rows())
                .singleElement()
                .extracting(ItineraryPreview.Row::hasProblem)
                .isEqualTo(false);
        assertThat(preview.problemSummary())
                .isEmpty();
    }

    @Test
    void everyUnknownAirportAndTheUnreadablePasteCountAsProblems() {
        ItineraryEvaluation evaluation = new ItineraryEvaluation(List.of("No confirmation number found"),
                "", List.of(), List.of(AirportCode.of("YQB"), AirportCode.of("YHZ")), null);

        assertThat(ItineraryPreview.from(evaluation).problemSummary())
                .isEqualTo("3 problems to fix below.");
    }

    @Test
    void aMovedLegCarriesTheOldValueOnlyForTheTimeThatChanged() {
        ItineraryPreview preview = ItineraryPreview.from(scheduleChange(
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 7, 10, 12, 45))));

        ItineraryPreview.Row row = preview.rows().getFirst();
        assertThat(row.status())
                .isEqualTo("Moved");
        assertThat(row.before())
                .as("only the departure differs")
                .isEqualTo(new ItineraryPreview.Before("", "", "Sun, Oct 18, 6:10 AM", ""));
        assertThat(row.departs())
                .as("the row itself shows the new time")
                .isEqualTo("Sun, Oct 18, 7:10 AM");
    }

    @Test
    void aMovedArrivalCarriesItsOldValueAndLeavesTheDepartureAlone() {
        ItineraryPreview preview = ItineraryPreview.from(scheduleChange(
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 13, 15))));

        assertThat(preview.rows().getFirst().before())
                .isEqualTo(new ItineraryPreview.Before("", "", "", "Sun, Oct 18, 12:45 PM"));
    }

    @Test
    void aRenumberedLegCarriesTheOldFlightNumber() {
        ItineraryPreview preview = ItineraryPreview.from(scheduleChange(
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA777", "SFO", "ORD", 6, 10, 12, 45))));

        assertThat(preview.rows().getFirst().before())
                .isEqualTo(new ItineraryPreview.Before("UA2091", "", "", ""));
    }

    @Test
    void aReroutedLegCarriesTheOldRoute() {
        ItineraryPreview preview = ItineraryPreview.from(scheduleChange(
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "DEN", 6, 10, 12, 45))));

        assertThat(preview.rows().getFirst().before())
                .isEqualTo(new ItineraryPreview.Before("", "SFO→ORD", "", ""));
    }

    @Test
    void everyKindOfLegSaysWhatHappensToItAndADroppedOneHasNoPasteNumber() {
        ItineraryPreview preview = ItineraryPreview.from(scheduleChange(
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45),
                        leg("UA3509", "ORD", "YOW", 14, 0, 17, 0)),
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45),
                        leg("UA9", "ORD", "YYZ", 15, 0, 16, 0))));

        assertThat(preview.rows())
                .extracting(ItineraryPreview.Row::status, ItineraryPreview.Row::flightNumber, ItineraryPreview.Row::number)
                .as("in departure order; the dropped leg is not in the paste")
                .containsExactly(
                        tuple("Unchanged", "UA2091", 1),
                        tuple("Removed", "UA3509", 0),
                        tuple("Added", "UA9", 2));
        assertThat(preview.rows())
                .extracting(ItineraryPreview.Row::statusClass)
                .containsExactly("unchanged", "removed", "added");
        assertThat(preview.rows())
                .extracting(ItineraryPreview.Row::before)
                .as("only a moved leg has anything to strike through")
                .containsOnly(ItineraryPreview.Before.NONE);
        assertThat(preview.changeable())
                .isTrue();
        assertThat(preview.nothingToChange())
                .isFalse();
    }

    @Test
    void aPasteMatchingWhatIsBookedHasNothingToChangeAndNothingToApply() {
        ItineraryPreview preview = ItineraryPreview.from(scheduleChange(
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45))));

        assertThat(preview.scheduleChange())
                .isTrue();
        assertThat(preview.nothingToChange())
                .isTrue();
        assertThat(preview.changeable())
                .isFalse();
    }

    @Test
    void aRefusedLegInAScheduleChangeIsAProblemAndNothingToApplyOrToSayNothingAbout() {
        ItineraryPreview preview = ItineraryPreview.from(scheduleChange(
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 12, 45, 6, 10))));

        assertThat(preview.rows().getFirst().problem())
                .isEqualTo("Arrives before it departs");
        assertThat(preview.changeable())
                .isFalse();
        assertThat(preview.nothingToChange())
                .as("it is not 'nothing to change' when something is wrong")
                .isFalse();
        assertThat(preview.problemSummary())
                .isEqualTo("1 problem to fix below.");
    }

    private static ItineraryLeg leg(String flightNumber, String from, String to,
                                    int departHour, int departMinute, int arriveHour, int arriveMinute) {
        ZoneId chicago = ZoneId.of("America/Chicago");
        return new ItineraryLeg(FlightId.of(UUID.randomUUID()), "United Airlines", flightNumber,
                AirportCode.of(from), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, departHour, departMinute), chicago),
                AirportCode.of(to), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, arriveHour, arriveMinute), chicago));
    }

    private static ItineraryEvaluation scheduleChange(List<ItineraryLeg> booked, List<ItineraryLeg> pasted) {
        ChangeFlightItineraryCommand command = new ChangeFlightItineraryCommand(
                FlightItineraryId.of(UUID.randomUUID()), pasted);
        ChangeFlightItineraryContext context = new ChangeFlightItineraryContext(true, booked, List.of(),
                ScheduledLegs.none(), Instant.parse("2026-10-03T12:00:00Z"));
        return new ItineraryEvaluation(List.of(), "MD7LKB", List.of(), List.of(), null, false,
                new ItineraryEvaluation.ScheduleChange(command, context, command.plan(context)));
    }

    private static ItineraryEvaluation evaluation(RuntimeException refusal) {
        PastedItinerary.Leg leg = new PastedItinerary.Leg(1, "United Airlines", "UA2091",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 10, 18, 6, 10),
                AirportCode.of("ORD"), LocalDateTime.of(2026, 10, 18, 12, 45));
        return new ItineraryEvaluation(List.of(), "MD7LKB",
                List.of(new ItineraryEvaluation.Leg(leg, refusal)), List.of(), null);
    }
}
