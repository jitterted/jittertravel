package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
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

/**
 * A paste whose confirmation code is already a live itinerary is a schedule change. The real
 * parser, zone table, fold and command, behind a spy executor that records what would be written.
 */
class FlightItineraryChangeTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final FlightItineraryId FORM = FlightItineraryId.of(UUID.randomUUID());
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

    @Test
    void aMovedLegIsShownAsMovedWithTheTimeItWasAndNothingIsWrittenByEvaluate() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        ItineraryEvaluation evaluation = booking(executor).evaluate(
                UNCHANGED_PASTE.replace("06:10 AM", "07:10 AM"), Map.of(), FORM, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.isScheduleChange())
                .as("the code names a live itinerary")
                .isTrue();
        assertThat(evaluation.bookable())
                .as("a change is not a booking")
                .isFalse();
        assertThat(evaluation.changeable())
                .isTrue();
        assertThat(evaluation.scheduleChange().plan().diffs())
                .extracting(LegDiff::kind)
                .containsExactly(Kind.CHANGED, Kind.UNCHANGED);
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void applyingTheChangeWritesTheLegsAndTheItineraryInOneCommand() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        booking(executor).book("request", UNCHANGED_PASTE.replace("06:10 AM", "07:10 AM"), Map.of(),
                FORM, NEW_FLIGHT_ID, NOW);

        assertThat(executor.commandId)
                .as("the form's id is the command id, as for a booking")
                .isEqualTo(FORM.id());
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
    void aPasteMatchingWhatIsBookedOffersNothingToApplyAndWritesNothing() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        ItineraryEvaluation evaluation = booking(executor)
                .book("request", UNCHANGED_PASTE, Map.of(), FORM, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.isScheduleChange())
                .isTrue();
        assertThat(evaluation.changeable())
                .isFalse();
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void aLegCancelledOnItsOwnEarlierIsRefusedWhenThePasteListsItAgain() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightCancelled(second, "", NOW));

        ItineraryEvaluation evaluation = booking(executor)
                .book("request", UNCHANGED_PASTE, Map.of(), FORM, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.scheduleChange().plan().diffs())
                .filteredOn(LegDiff::refused)
                .extracting(LegDiff::pasteNumber)
                .containsExactly(2);
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void aPasteOfACancelledItineraryIsRefusedWithTheReasonAndBooksNothing() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightItineraryCancelled(TRIP, "", NOW));

        ItineraryEvaluation evaluation = booking(executor)
                .book("request", UNCHANGED_PASTE, Map.of(), FORM, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.parseProblems())
                .containsExactly("Itinerary MD7LKB was cancelled; it cannot be changed");
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void aCodeNoItineraryHasIsStillANewBooking() {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);

        ItineraryEvaluation evaluation = booking(executor).evaluate(
                UNCHANGED_PASTE.replace("MD7LKB", "ZZZZZZ").replace("Sun, Oct 18", "Tue, Oct 20"),
                Map.of(), FORM, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.isScheduleChange())
                .isFalse();
        assertThat(evaluation.bookable())
                .isTrue();
    }

    @Test
    void aSecondChangeSeesTheMembershipTheFirstLeft() {
        FlightId added = FlightId.random();
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip,
                new FlightBooked(added, "United Airlines", "UA55", AirportCode.of("SFO"),
                        at(PACIFIC, 19, 0), AirportCode.of("LAX"), at(PACIFIC, 20, 0)),
                new FlightItineraryChanged(TRIP, List.of(first, second, added), "", NOW));

        ItineraryEvaluation evaluation = booking(executor)
                .evaluate(UNCHANGED_PASTE, Map.of(), FORM, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.scheduleChange().plan().diffs())
                .extracting(LegDiff::kind)
                .as("the leg the first change added is now booked, and this paste does not list it")
                .containsExactly(Kind.UNCHANGED, Kind.UNCHANGED, Kind.REMOVED);
    }

    @Test
    void theStoredChangeCommandCarriesNoPrivateLineOfTheEmail() throws Exception {
        SpyCommandExecutor executor = new SpyCommandExecutor(sfoOrd, ordSfo, trip);
        String withPrivateLines = UNCHANGED_PASTE.replace("06:10 AM", "07:10 AM")
                + "eTicket number: 0000000000000\nFrequent Flyer: UA-XXXXX000\n";

        booking(executor).book("request", withPrivateLines, Map.of(), FORM, NEW_FLIGHT_ID, NOW);

        assertThat(EventJsonMapperFactory.create().writeValueAsString(executor.command))
                .doesNotContain("0000000000000")
                .doesNotContain("UA-XXXXX000");
    }

    private static ZonedTimestamp at(ZoneId zone, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, hour, minute), zone);
    }

    private static FlightItineraryBooking booking(SpyCommandExecutor executor) {
        return new FlightItineraryBooking(executor, new AirportZoneResolver(), new LiveScheduledLegs(executor));
    }

    /** A spy: serves a fixed log for decisions, and records what it was asked to write. */
    private static final class SpyCommandExecutor extends CommandExecutor {
        private final List<StoredEvent> log;
        private final List<Event> emitted = new ArrayList<>();
        private UUID commandId;
        private Object command;

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
