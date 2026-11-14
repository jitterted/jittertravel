package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryChangeEvaluation;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ChangeFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryContext;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import dev.ted.jittertravel.domain.ItineraryChangePlan;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.ScheduledLegs;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ItineraryChangePreviewTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void aMovedLegCarriesTheOldValueOnlyForTheTimeThatChanged() {
        ItineraryChangePreview preview = preview(ItineraryChangeKind.SCHEDULE_CHANGE,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 7, 10, 12, 45)));

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
        ItineraryChangePreview preview = preview(ItineraryChangeKind.SCHEDULE_CHANGE,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 13, 15)));

        assertThat(preview.rows().getFirst().before())
                .isEqualTo(new ItineraryPreview.Before("", "", "", "Sun, Oct 18, 12:45 PM"));
    }

    @Test
    void aRenumberedLegCarriesTheOldFlightNumber() {
        ItineraryChangePreview preview = preview(ItineraryChangeKind.SCHEDULE_CHANGE,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA777", "SFO", "ORD", 6, 10, 12, 45)));

        assertThat(preview.rows().getFirst().before())
                .isEqualTo(new ItineraryPreview.Before("UA2091", "", "", ""));
    }

    @Test
    void aReroutedLegCarriesTheOldRoute() {
        ItineraryChangePreview preview = preview(ItineraryChangeKind.SCHEDULE_CHANGE,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "DEN", 6, 10, 12, 45)));

        assertThat(preview.rows().getFirst().before())
                .isEqualTo(new ItineraryPreview.Before("", "SFO→ORD", "", ""));
    }

    @Test
    void everyKindOfLegSaysWhatHappensToItAndADroppedOneHasNoNumber() {
        ItineraryChangePreview preview = preview(ItineraryChangeKind.REBOOKING,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45),
                        leg("UA3509", "ORD", "YOW", 14, 0, 17, 0)),
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45),
                        leg("UA9", "ORD", "YYZ", 15, 0, 16, 0)));

        assertThat(preview.rows())
                .extracting(ItineraryPreview.Row::status, ItineraryPreview.Row::flightNumber, ItineraryPreview.Row::number)
                .as("in departure order; the dropped leg is not in the email")
                .containsExactly(
                        tuple("Unchanged", "UA2091", 1),
                        tuple("Dropped", "UA3509", 0),
                        tuple("Added", "UA9", 2));
        assertThat(preview.rows())
                .extracting(ItineraryPreview.Row::statusClass)
                .containsExactly("unchanged", "dropped", "added");
        assertThat(preview.rows())
                .extracting(ItineraryPreview.Row::before)
                .as("only a moved leg has anything to strike through")
                .containsOnly(ItineraryPreview.Before.NONE);
        assertThat(preview.summary())
                .isEqualTo("Recorded as your rebooking: 1 leg added, 1 dropped, 1 unchanged.");
        assertThat(preview.applicable())
                .isTrue();
        assertThat(preview.applyLabel())
                .isEqualTo("Record rebooking");
    }

    @Test
    void aScheduleChangeIsRecordedAsTheAirlinesAndSaysSoOnItsButton() {
        ItineraryChangePreview preview = preview(ItineraryChangeKind.SCHEDULE_CHANGE,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45),
                        leg("UA3509", "ORD", "YOW", 14, 0, 17, 0)),
                List.of(leg("UA2091", "SFO", "ORD", 7, 10, 13, 45),
                        leg("UA3509", "ORD", "YOW", 15, 0, 18, 0)));

        assertThat(preview.summary())
                .as("the noun is said once, on the first count")
                .isEqualTo("Recorded as the airline's schedule change: 2 legs moved.");
        assertThat(preview.applyLabel())
                .isEqualTo("Record schedule change");
    }

    @Test
    void withoutSayingWhoChangedItTheDiffShowsAndThePickIsTheProblem() {
        ItineraryChangePreview preview = preview(null,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 7, 10, 12, 45)));

        assertThat(preview.planned())
                .isTrue();
        assertThat(preview.kindProblem())
                .isEqualTo("Pick who changed it");
        assertThat(preview.problemSummary())
                .isEqualTo("1 problem to fix below.");
        assertThat(preview.summary())
                .as("counted, but not yet recorded as anybody's")
                .isEqualTo("1 leg moved.");
        assertThat(preview.applicable())
                .isFalse();
        assertThat(preview.applyLabel())
                .isEmpty();
    }

    @Test
    void aPasteMatchingWhatIsBookedHasNothingToChangeAndNothingToApply() {
        ItineraryChangePreview preview = preview(ItineraryChangeKind.REBOOKING,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)));

        assertThat(preview.nothingToChange())
                .isTrue();
        assertThat(preview.applicable())
                .isFalse();
        assertThat(preview.summary())
                .isEmpty();
    }

    @Test
    void aRefusedLegIsAProblemAndNothingToApplyOrToSayNothingAbout() {
        ItineraryChangePreview preview = preview(ItineraryChangeKind.REBOOKING,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 12, 45, 6, 10)));

        assertThat(preview.rows().getFirst().problem())
                .isEqualTo("Arrives before it departs");
        assertThat(preview.applicable())
                .isFalse();
        assertThat(preview.nothingToChange())
                .as("it is not 'nothing to change' when something is wrong")
                .isFalse();
        assertThat(preview.problemSummary())
                .isEqualTo("1 problem to fix below.");
    }

    @Test
    void anUnreadEmailAndAMissingPickAreCountedTogether() {
        ItineraryChangePreview preview = ItineraryChangePreview.from(new ItineraryChangeEvaluation(
                List.of("This email is for MD7LKB, not HE80PP"), List.of(), null, null, null, null));

        assertThat(preview.parseProblem())
                .isEqualTo("This email is for MD7LKB, not HE80PP");
        assertThat(preview.planned())
                .isFalse();
        assertThat(preview.problemSummary())
                .isEqualTo("2 problems to fix below.");
    }

    @Test
    void aMissingPickAndARefusedLegAreTwoProblems() {
        ItineraryChangePreview preview = preview(null,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 12, 45, 6, 10)));

        assertThat(preview.problemCount())
                .isEqualTo(2);
    }

    @Test
    void aMovedLegSaysWhichOfItsCellsCarryAnOldValue() {
        ItineraryPreview.Before onlyDeparts = new ItineraryPreview.Before("", "", "Sun, Oct 18, 6:10 AM", "");
        ItineraryPreview.Before allButDeparts = new ItineraryPreview.Before("UA2090", "SFO→ORD", "", "b");

        assertThat(List.of(onlyDeparts.hasFlightNumber(), onlyDeparts.hasRoute(),
                onlyDeparts.hasDeparts(), onlyDeparts.hasArrives()))
                .containsExactly(false, false, true, false);
        assertThat(List.of(allButDeparts.hasFlightNumber(), allButDeparts.hasRoute(),
                allButDeparts.hasDeparts(), allButDeparts.hasArrives()))
                .containsExactly(true, true, false, true);
    }

    @Test
    void aMissingPickAnUnreadEmailAndEachUnknownAirportAreCountedTogether() {
        ItineraryChangePreview preview = ItineraryChangePreview.from(new ItineraryChangeEvaluation(
                List.of("No confirmation number found"), List.of(AirportCode.of("YQB"), AirportCode.of("YHZ")),
                null, null, null, null));

        assertThat(preview.problemCount())
                .isEqualTo(4);
    }

    @Test
    void aRowHasAStatusOnlyOnTheChangePage() {
        ItineraryChangePreview preview = preview(ItineraryChangeKind.REBOOKING,
                List.of(leg("UA2091", "SFO", "ORD", 6, 10, 12, 45)),
                List.of(leg("UA2091", "SFO", "ORD", 7, 10, 12, 45)));
        ItineraryPreview.Row bookRow = new ItineraryPreview.Row(1, "UA2091", "SFO→ORD", "a", "b",
                "", "", "", "", ItineraryPreview.Before.NONE);

        assertThat(preview.rows().getFirst().hasStatus())
                .isTrue();
        assertThat(bookRow.hasStatus())
                .isFalse();
    }

    @Test
    void beforeAnyPreviewThereIsNothingToCount() {
        assertThat(ItineraryChangePreview.NONE.problemCount())
                .isZero();
        assertThat(ItineraryChangePreview.NONE.planned())
                .isFalse();
    }

    private static ItineraryChangePreview preview(ItineraryChangeKind kind, List<ItineraryLeg> booked,
                                                  List<ItineraryLeg> pasted) {
        ChangeFlightItineraryContext context = new ChangeFlightItineraryContext(true, booked, List.of(),
                ScheduledLegs.none(), NOW);
        ChangeFlightItineraryCommand command = kind == null
                ? null
                : new ChangeFlightItineraryCommand(FlightItineraryId.of(UUID.randomUUID()), kind, pasted);
        return ItineraryChangePreview.from(new ItineraryChangeEvaluation(List.of(), List.of(), kind,
                ItineraryChangePlan.of(pasted, context), context, command));
    }

    private static ItineraryLeg leg(String flightNumber, String from, String to,
                                    int departHour, int departMinute, int arriveHour, int arriveMinute) {
        ZoneId chicago = ZoneId.of("America/Chicago");
        return new ItineraryLeg(FlightId.of(UUID.randomUUID()), "United Airlines", flightNumber,
                AirportCode.of(from), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, departHour, departMinute), chicago),
                AirportCode.of(to), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, arriveHour, arriveMinute), chicago));
    }
}
