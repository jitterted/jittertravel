package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancellationCause;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryRebooked;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import dev.ted.jittertravel.domain.ItineraryChangePlan.Kind;
import dev.ted.jittertravel.domain.ItineraryChangePlan.LegDiff;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventJsonMapperFactory;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Changing a booked itinerary from its own page: the real parser, zone table, fold and plan, behind
 * a spy executor that records what would be written.
 */
class FlightItineraryChangeTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final UUID COMMAND = UUID.randomUUID();
    private static final FlightItineraryId TRIP = FlightItineraryId.of(UUID.randomUUID());
    private static final Supplier<FlightId> NEW_FLIGHT_ID = () -> FlightId.of(UUID.randomUUID());
    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");

    private final FlightId first = FlightId.random();
    private final FlightId second = FlightId.random();

    private final FlightBooked sfoOrd = new FlightBooked(first, "United Airlines", "UA2091",
            AirportCode.of("SFO"), at(PACIFIC, 6, 10), AirportCode.of("ORD"), at(CHICAGO, 12, 45));
    private final FlightBooked ordSfo = new FlightBooked(second, "United Airlines", "UA3000",
            AirportCode.of("ORD"), at(CHICAGO, 14, 0), AirportCode.of("SFO"), at(PACIFIC, 17, 0));
    private final FlightItineraryBooked trip = new FlightItineraryBooked(TRIP, "United Airlines", "MD7LKB",
            List.of(first, second));

    private static final String UNCHANGED_PASTE = """
            Confirmation Number:
            MD7LKB
            Flight 1 of 2 UA2091        Class: United First (Z)
            Sun, Oct 18, 2026        Sun, Oct 18, 2026
            06:10 AM        12:45 PM
            San Francisco, CA, US (SFO)        Chicago, IL, US (ORD)
            Flight 2 of 2 UA3000        Class: United Business (Z)
            Sun, Oct 18, 2026        Sun, Oct 18, 2026
            02:00 PM        05:00 PM
            Chicago, IL, US (ORD)        San Francisco, CA, US (SFO)
            """;

    private static final String MOVED_PASTE = UNCHANGED_PASTE.replace("06:10 AM", "07:10 AM");

    @Test
    void aMovedLegIsShownAsMovedAndNothingIsWrittenByEvaluate() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        ItineraryChangeEvaluation evaluation = change(executor).evaluate(TRIP, ItineraryChangeKind.SCHEDULE_CHANGE,
                MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.applicable())
                .isTrue();
        assertThat(evaluation.plan().diffs())
                .extracting(LegDiff::kind)
                .containsExactly(Kind.CHANGED, Kind.UNCHANGED);
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void applyingAScheduleChangeWritesTheLegsAndTheItineraryInOneCommand() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        change(executor).apply(COMMAND, "request", TRIP, ItineraryChangeKind.SCHEDULE_CHANGE,
                MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(executor.commandId)
                .as("the id minted at the boundary is the command id")
                .isEqualTo(COMMAND);
        assertThat(executor.emitted)
                .hasSize(2);
        assertThat(executor.emitted.get(0))
                .isInstanceOf(FlightChanged.class);
        assertThat(((FlightChanged) executor.emitted.get(0)).flightId())
                .isEqualTo(first);
        assertThat(executor.emitted.get(1))
                .isEqualTo(new FlightItineraryChanged(TRIP, List.of(first, second),
                        "Airline schedule change", NOW));
    }

    @Test
    void applyingARebookingRecordsItAsTedsOwn() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        change(executor).apply(COMMAND, "request", TRIP, ItineraryChangeKind.REBOOKING,
                MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(executor.emitted.get(1))
                .isEqualTo(new FlightItineraryRebooked(TRIP, List.of(first, second), "Rebooked", NOW));
    }

    @Test
    void withoutSayingWhoChangedItTheDiffIsShownButNothingCanBeApplied() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        ItineraryChangeEvaluation evaluation = change(executor).apply(COMMAND, "request", TRIP, null,
                MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.kindMissing())
                .isTrue();
        assertThat(evaluation.plan().diffs())
                .as("the diff does not depend on who made the change")
                .extracting(LegDiff::kind)
                .containsExactly(Kind.CHANGED, Kind.UNCHANGED);
        assertThat(evaluation.applicable())
                .isFalse();
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void anEmailForAnotherConfirmationCodeIsRefusedAndNotAppliedToThatOne() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        ItineraryChangeEvaluation evaluation = change(executor).apply(COMMAND, "request", TRIP,
                ItineraryChangeKind.REBOOKING, MOVED_PASTE.replace("MD7LKB", "HE80PP"), Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.parseProblems())
                .containsExactly("This email is for HE80PP, not MD7LKB");
        assertThat(evaluation.planned())
                .isFalse();
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void aPasteMatchingWhatIsBookedHasNothingToChangeAndWritesNothing() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        ItineraryChangeEvaluation evaluation = change(executor).apply(COMMAND, "request", TRIP,
                ItineraryChangeKind.SCHEDULE_CHANGE, UNCHANGED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.nothingToChange())
                .isTrue();
        assertThat(evaluation.applicable())
                .isFalse();
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void aLegCancelledOnItsOwnEarlierIsRefusedWhenThePasteListsItAgain() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightCancelled(second, "", NOW));

        ItineraryChangeEvaluation evaluation = change(executor).apply(COMMAND, "request", TRIP,
                ItineraryChangeKind.REBOOKING, UNCHANGED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.plan().diffs())
                .filteredOn(LegDiff::refused)
                .extracting(LegDiff::pasteNumber)
                .containsExactly(2);
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void aLegEditedByHandIsComparedAtItsCurrentTimesNotTheOnesItWasBookedWith() {
        FlightChanged movedByHand = new FlightChanged(first, "United Airlines", "UA2091",
                AirportCode.of("SFO"), at(PACIFIC, 7, 10), AirportCode.of("ORD"), at(CHICAGO, 12, 45),
                "Edited");
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip, movedByHand);

        ItineraryChangeEvaluation evaluation = change(executor).evaluate(TRIP, ItineraryChangeKind.SCHEDULE_CHANGE,
                MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.plan().diffs())
                .as("the paste agrees with the edited leg, so nothing differs")
                .extracting(LegDiff::kind)
                .containsExactly(Kind.UNCHANGED, Kind.UNCHANGED);
    }

    @Test
    void aLegAnEarlierScheduleChangeDroppedIsBookedAgainWhenTheAirlinePutsItBack() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightCancelled(second, "Airline schedule change", NOW,
                        FlightCancellationCause.AIRLINE_SCHEDULE_CHANGE));

        change(executor).apply(COMMAND, "request", TRIP, ItineraryChangeKind.SCHEDULE_CHANGE,
                UNCHANGED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(executor.emitted)
                .hasSize(2);
        assertThat(executor.emitted.get(0))
                .isInstanceOf(FlightBooked.class);
        FlightBooked reinstated = (FlightBooked) executor.emitted.get(0);
        assertThat(reinstated.flightId())
                .as("cancelling cannot be undone, so the leg comes back as a new flight")
                .isNotEqualTo(second);
        assertThat(reinstated.flightNumber())
                .isEqualTo("UA3000");
        assertThat(((FlightItineraryChanged) executor.emitted.get(1)).flightIds())
                .as("the dropped leg stays in the trip, cancelled, beside the one that replaced it")
                .containsExactlyInAnyOrder(first, second, reinstated.flightId());
    }

    /** D2: Ted's own rebooking drops a leg too, so changing back to the original works. */
    @Test
    void aLegAnEarlierRebookingDroppedIsBookedAgainWhenAPasteListsIt() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightCancelled(second, "Rebooked", NOW, FlightCancellationCause.REBOOKED));

        ItineraryChangeEvaluation evaluation = change(executor).evaluate(TRIP, ItineraryChangeKind.REBOOKING,
                UNCHANGED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.plan().diffs())
                .extracting(LegDiff::kind, LegDiff::refused)
                .as("added again, not refused as cancelled earlier")
                .containsExactly(tuple(Kind.UNCHANGED, false), tuple(Kind.ADDED, false));
    }

    @Test
    void aSecondChangeSeesTheMembershipARebookingLeft() {
        FlightId added = FlightId.random();
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightBooked(added, "United Airlines", "UA55", AirportCode.of("SFO"),
                        at(PACIFIC, 19, 0), AirportCode.of("LAX"), at(PACIFIC, 20, 0)),
                new FlightItineraryRebooked(TRIP, List.of(first, second, added), "Rebooked", NOW));

        ItineraryChangeEvaluation evaluation = change(executor).evaluate(TRIP, ItineraryChangeKind.REBOOKING,
                UNCHANGED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.plan().diffs())
                .extracting(LegDiff::kind)
                .as("the leg the rebooking added is now booked, and this paste does not list it")
                .containsExactly(Kind.UNCHANGED, Kind.UNCHANGED, Kind.REMOVED);
    }

    @Test
    void aSecondChangeSeesTheMembershipAScheduleChangeLeft() {
        FlightId added = FlightId.random();
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightBooked(added, "United Airlines", "UA55", AirportCode.of("SFO"),
                        at(PACIFIC, 19, 0), AirportCode.of("LAX"), at(PACIFIC, 20, 0)),
                new FlightItineraryChanged(TRIP, List.of(first, second, added), "", NOW));

        ItineraryChangeEvaluation evaluation = change(executor).evaluate(TRIP, ItineraryChangeKind.SCHEDULE_CHANGE,
                UNCHANGED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.plan().diffs())
                .extracting(LegDiff::kind)
                .containsExactly(Kind.UNCHANGED, Kind.UNCHANGED, Kind.REMOVED);
    }

    @Test
    void aCancelledItineraryCannotBeChanged() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightItineraryCancelled(TRIP, "", NOW));

        ItineraryChangeEvaluation evaluation = change(executor).apply(COMMAND, "request", TRIP,
                ItineraryChangeKind.REBOOKING, MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.parseProblems())
                .containsExactly("Itinerary MD7LKB was cancelled; it cannot be changed");
        assertThat(executor.emitted)
                .isEmpty();
    }

    /**
     * The command writes a trip's legs in the same append as the itinerary that names them, so a
     * member with no booking in the log is a corrupt log. The fold says which, rather than diffing
     * the paste against a trip missing a leg.
     */
    @Test
    void anItineraryNamingAFlightTheLogNeverBookedFailsByName() {
        FlightId neverBooked = FlightId.random();
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo,
                new FlightItineraryBooked(TRIP, "United Airlines", "MD7LKB", List.of(first, neverBooked)));

        assertThatThrownBy(() -> change(executor).evaluate(TRIP, ItineraryChangeKind.REBOOKING,
                UNCHANGED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Itinerary " + TRIP.id() + " names flight " + neverBooked.id()
                            + ", which was never booked");
    }

    @Test
    void theStoredChangeCommandCarriesNoPrivateLineOfTheEmail() throws Exception {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);
        String withPrivateLines = MOVED_PASTE + "eTicket number: 0000000000000\nFrequent Flyer: UA-XXXXX000\n";

        change(executor).apply(COMMAND, "request", TRIP, ItineraryChangeKind.REBOOKING,
                withPrivateLines, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(EventJsonMapperFactory.create().writeValueAsString(executor.command))
                .doesNotContain("0000000000000")
                .doesNotContain("UA-XXXXX000");
    }

    /**
     * The first real mid-trip email (Ted, 2026-11-14): HE80PP rebooked to fly home from FRA, so
     * BER->FRA goes. It lists both flown legs, and its seat block still names BER-FRA with no seat,
     * which must not be read as a leg. The private lines are replaced with placeholders.
     */
    @Test
    void theHe80ppRebookingDropsBerFraAndLeavesTheFlownLegsAlone() {
        ZoneId newYork = ZoneId.of("America/New_York");
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        FlightId sfoEwr = FlightId.random();
        FlightId ewrBer = FlightId.random();
        FlightId berFra = FlightId.random();
        FlightId fraSfo = FlightId.random();
        FlightItineraryId he80pp = FlightItineraryId.of(UUID.randomUUID());
        SpyCommandExecutor executor = new SpyCommandExecutor(
                new FlightBooked(sfoEwr, "United Airlines", "UA1642", AirportCode.of("SFO"),
                        on(11, 7, 8, 15, PACIFIC), AirportCode.of("EWR"), on(11, 7, 16, 38, newYork)),
                new FlightBooked(ewrBer, "United Airlines", "UA962", AirportCode.of("EWR"),
                        on(11, 7, 17, 50, newYork), AirportCode.of("BER"), on(11, 8, 7, 55, berlin)),
                new FlightBooked(berFra, "United Airlines", "UA8786", AirportCode.of("BER"),
                        on(11, 26, 10, 40, berlin), AirportCode.of("FRA"), on(11, 26, 11, 50, berlin)),
                new FlightBooked(fraSfo, "United Airlines", "UA59", AirportCode.of("FRA"),
                        on(11, 26, 13, 30, berlin), AirportCode.of("SFO"), on(11, 26, 16, 5, PACIFIC)),
                new FlightItineraryBooked(he80pp, "United Airlines", "HE80PP",
                        List.of(sfoEwr, ewrBer, berFra, fraSfo)));
        String email = """
                Confirmation Number:
                HE80PP
                Flight 1 of 3 UA1642        Class: United Economy (B)
                Sat, Nov 07, 2026        Sat, Nov 07, 2026
                08:15 AM        04:38 PM
                San Francisco, CA, US (SFO)        Newark, NJ/New York, NY, US (EWR)
                Flight 2 of 3 UA962        Class: United Premium Plus (A)
                Sat, Nov 07, 2026        Sun, Nov 08, 2026
                05:50 PM        07:55 AM
                Newark, NJ/New York, NY, US (EWR)        Berlin, DE (BER)
                Flight 3 of 3 UA59        Class: United Premium Plus (R)
                Thu, Nov 26, 2026        Thu, Nov 26, 2026
                01:30 PM        04:05 PM
                Frankfurt, DE (FRA)        San Francisco, CA, US (SFO)
                Traveler Details
                TRAVELER/NAME
                eTicket number: 0000000000000    Seats: SFO-EWR 30C
                Frequent Flyer: UA-XXXXX000 Premier Platinum    EWR-BER 21A
                FRA-SFO 21K
                Preferred Zone seat (0000000000000)    BER-FRA
                """;

        change(executor).apply(COMMAND, "request", he80pp, ItineraryChangeKind.REBOOKING, email, Map.of(),
                NEW_FLIGHT_ID, Instant.parse("2026-11-14T15:00:00Z"));

        assertThat(executor.emitted)
                .containsExactly(
                        new FlightCancelled(berFra, "Rebooked", Instant.parse("2026-11-14T15:00:00Z"),
                                FlightCancellationCause.REBOOKED),
                        new FlightItineraryRebooked(he80pp, List.of(sfoEwr, ewrBer, berFra, fraSfo), "Rebooked",
                                Instant.parse("2026-11-14T15:00:00Z")));
    }

    /** The preview's family line comes from the plan's result, so a dropped leg is gone from it. */
    @Test
    void theEvaluationSaysWhetherFamilyWillBeEmailedFromTheLegsLeftAfterTheChange() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);
        String withoutTheReturn = """
                Confirmation Number:
                MD7LKB
                Flight 1 of 1 UA2091        Class: United First (Z)
                Sun, Oct 18, 2026        Sun, Oct 18, 2026
                06:10 AM        12:45 PM
                San Francisco, CA, US (SFO)        Chicago, IL, US (ORD)
                """;

        ItineraryChangeEvaluation dropped = change(executor).evaluate(TRIP, ItineraryChangeKind.REBOOKING,
                withoutTheReturn, Map.of(), NEW_FLIGHT_ID, NOW);
        ItineraryChangeEvaluation movedAnHour = change(executor).evaluate(TRIP, ItineraryChangeKind.REBOOKING,
                MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(dropped.familyNews())
                .as("the trip now ends at ORD, not SFO")
                .map(ItineraryChangeNews.Difference::kind)
                .hasValue(ItineraryChangeNews.Kind.ENDS);
        assertThat(movedAnHour.familyNews())
                .as("an hour earlier is not news")
                .isEmpty();
    }

    @Test
    void theItineraryThePageNamesDecidesTheCodeNotTheFirstOneInTheLog() {
        FlightItineraryBooked earlierTrip = new FlightItineraryBooked(FlightItineraryId.of(UUID.randomUUID()),
                "United Airlines", "HE80PP", List.of());
        SpyCommandExecutor executor = new SpyCommandExecutor(earlierTrip, sfoOrd, ordSfo, trip);

        ItineraryChangeEvaluation evaluation = change(executor).evaluate(TRIP, ItineraryChangeKind.SCHEDULE_CHANGE,
                MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.parseProblems())
                .as("an MD7LKB email is right for the MD7LKB itinerary")
                .isEmpty();
    }

    @Test
    void anItineraryThatWasNeverBookedFailsByName() {
        FlightItineraryId unknown = FlightItineraryId.of(UUID.randomUUID());
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        assertThatThrownBy(() -> change(executor).evaluate(unknown, ItineraryChangeKind.REBOOKING,
                MOVED_PASTE, Map.of(), NEW_FLIGHT_ID, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No itinerary " + unknown.id() + " was booked");
    }

    @Test
    void itReportsWhateverModeTheExecutorIsIn() {
        SpyCommandExecutor writable = new SpyCommandExecutor();
        SpyCommandExecutor readOnly = new SpyCommandExecutor();
        readOnly.readOnly = true;

        assertThat(change(writable).isReadOnly())
                .as("writable executor")
                .isFalse();
        assertThat(change(readOnly).isReadOnly())
                .as("read-only executor")
                .isTrue();
    }

    private static ZonedTimestamp at(ZoneId zone, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, hour, minute), zone);
    }

    private static ZonedTimestamp on(int month, int day, int hour, int minute, ZoneId zone) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, month, day, hour, minute), zone);
    }

    private static FlightItineraryChange change(SpyCommandExecutor executor) {
        return new FlightItineraryChange(executor, new AirportZoneResolver(), new LiveScheduledLegs(executor));
    }

    /** A spy: serves a fixed log for decisions, and records what it was asked to write. */
    private static final class SpyCommandExecutor extends CommandExecutor {
        private final List<StoredEvent> log;
        private final List<Event> emitted = new ArrayList<>();
        private UUID commandId;
        private Object command;
        private boolean readOnly;

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        SpyCommandExecutor(Event... existing) {
            super(null, null);
            this.log = Stream.of(existing)
                    .map(e -> new StoredEvent(1, e.getClass(), UUID.randomUUID(),
                            Instant.parse("2026-01-01T00:00:00Z"), e, UUID.randomUUID()))
                    .toList();
        }

        @Override
        public Stream<StoredEvent> eventsForDecision() {
            return log.stream();
        }

        @Override
        public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                        DomainCommand<C> command) {
            this.commandId = commandId;
            this.command = command;
            command.execute(context).forEach(emitted::add);
        }
    }
}
