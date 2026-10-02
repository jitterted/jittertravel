package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookFlightItineraryCommand;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
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
 * The real parser, the real zone table and the real command, behind a spy executor that records
 * what would be written.
 */
class FlightItineraryBookingTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final FlightItineraryId ITINERARY = FlightItineraryId.of(UUID.randomUUID());
    private static final Supplier<FlightId> NEW_FLIGHT_ID = () -> FlightId.of(UUID.randomUUID());

    // Ted's sample, trimmed to its first two legs, with its private lines replaced by placeholders.
    private static final String SAMPLE = """
            Confirmation Number:
            MD7LKB
            Flight 1 of 2 UA2091        Class: United First (Z)
            Sun, Oct 18, 2026        Sun, Oct 18, 2026
            06:10 AM        12:45 PM
            San Francisco, CA, US (SFO)        Chicago, IL, US (ORD)
            Flight 2 of 2 UA3509        Class: United Business (Z)
            Sun, Oct 18, 2026        Sun, Oct 18, 2026
            01:55 PM        05:16 PM
            Chicago, IL, US (ORD)        Ottawa, ON, CA (YOW)
            Traveler Details
            TRAVELER/SAMPLE
            eTicket number: 0000000000000    Seats: SFO-ORD 04B
            Frequent Flyer: UA-XXXXX000 Premier Gold
            """;

    @Test
    void aCleanPasteBooksEveryLegAndTheItineraryInOneCommand() {
        SpyCommandExecutor executor = new SpyCommandExecutor();
        FlightItineraryBooking booking = booking(executor);

        ItineraryEvaluation evaluation = booking.book("request", SAMPLE, Map.of(), ITINERARY, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.bookable())
                .isTrue();
        assertThat(executor.commandId)
                .as("the itinerary id is the command id, so one form cannot book the trip twice")
                .isEqualTo(ITINERARY.id());
        assertThat(executor.emitted)
                .as("two legs, then the itinerary")
                .hasSize(3);
        assertThat(executor.emitted.get(1))
                .isInstanceOf(FlightBooked.class);
        FlightBooked toOttawa = (FlightBooked) executor.emitted.get(1);
        assertThat(toOttawa.arrivalDateTime())
                .as("YOW resolved from the curated table")
                .isEqualTo(ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 17, 16),
                        ZoneId.of("America/Toronto")));
        assertThat(executor.emitted.get(2))
                .isInstanceOf(FlightItineraryBooked.class);
        FlightItineraryBooked itinerary = (FlightItineraryBooked) executor.emitted.get(2);
        assertThat(itinerary.confirmationCode())
                .isEqualTo("MD7LKB");
        assertThat(itinerary.flightIds())
                .containsExactly(((FlightBooked) executor.emitted.get(0)).flightId(), toOttawa.flightId());
    }

    @Test
    void anAirportTheTableDoesNotKnowWaitsForAZoneAndBooksNothing() {
        SpyCommandExecutor executor = new SpyCommandExecutor();
        String unknown = SAMPLE.replace("(YOW)", "(YQB)");

        ItineraryEvaluation evaluation = booking(executor)
                .book("request", unknown, Map.of(), ITINERARY, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.unresolvedAirports())
                .containsExactly(AirportCode.of("YQB"));
        assertThat(evaluation.bookable())
                .isFalse();
        assertThat(executor.emitted)
                .as("nothing is judged, let alone written, until every time has a zone")
                .isEmpty();
    }

    @Test
    void aPickedZoneSettlesAnUnknownAirport() {
        SpyCommandExecutor executor = new SpyCommandExecutor();
        String unknown = SAMPLE.replace("(YOW)", "(YQB)");

        ItineraryEvaluation evaluation = booking(executor).evaluate(unknown, Map.of("YQB", "CANADA_EASTERN"),
                ITINERARY, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.bookable())
                .isTrue();
        ItineraryLeg toQuebec = evaluation.command().legs().get(1);
        assertThat(toQuebec.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("America/Toronto"));
    }

    @Test
    void thePreviewShowsTheRefusalBookingWouldMeetAndWritesNothing() {
        // A flight already booked across the first leg's window.
        FlightBooked alreadyBooked = new FlightBooked(FlightId.of(UUID.randomUUID()), "United Airlines", "UA1",
                AirportCode.of("SFO"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 7, 0), ZoneId.of("America/Los_Angeles")),
                AirportCode.of("DEN"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 10, 0), ZoneId.of("America/Denver")));
        SpyCommandExecutor executor = new SpyCommandExecutor(alreadyBooked);

        ItineraryEvaluation evaluation = booking(executor)
                .book("request", SAMPLE, Map.of(), ITINERARY, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.bookable())
                .isFalse();
        assertThat(evaluation.legs())
                .extracting(leg -> leg.refused() ? leg.refusal().getClass().getSimpleName() : "none")
                .as("leg 1 collides with the booked flight; leg 2 is fine")
                .containsExactly(OverlappingLegRefused.class.getSimpleName(), "none");
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void aPasteThatCannotBeReadBooksNothing() {
        SpyCommandExecutor executor = new SpyCommandExecutor();

        ItineraryEvaluation evaluation = booking(executor)
                .book("request", "Thanks for choosing United!", Map.of(), ITINERARY, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.parseProblems())
                .isNotEmpty();
        assertThat(executor.emitted)
                .isEmpty();
    }

    /**
     * The command is what {@code command_log} stores, serialized by the same mapper, so this is the
     * stored row: the pasted email's private lines must not be in it.
     */
    @Test
    void theStoredCommandCarriesNoneOfThePrivateLinesOfTheEmail() throws Exception {
        SpyCommandExecutor executor = new SpyCommandExecutor();

        booking(executor).book("request", SAMPLE, Map.of(), ITINERARY, NEW_FLIGHT_ID, NOW);

        String stored = EventJsonMapperFactory.create().writeValueAsString(executor.command);
        assertThat(stored)
                .contains("\"confirmationCode\":\"MD7LKB\"")
                .doesNotContain("0000000000000")
                .doesNotContain("UA-XXXXX000")
                .doesNotContain("TRAVELER/SAMPLE")
                .doesNotContain("04B");
    }

    @Test
    void anItineraryIdAlreadyBookedIsReportedAsSuchAndWritesNothing() {
        FlightItineraryBooked earlier = new FlightItineraryBooked(ITINERARY, "United Airlines", "MD7LKB", List.of());
        SpyCommandExecutor executor = new SpyCommandExecutor(earlier);

        ItineraryEvaluation evaluation = booking(executor)
                .book("request", SAMPLE, Map.of(), ITINERARY, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.alreadyBooked())
                .isTrue();
        assertThat(evaluation.bookable())
                .as("there is no command to run")
                .isFalse();
        assertThat(executor.command)
                .as("the executor is never reached, so no command row is attempted")
                .isNull();
        assertThat(executor.emitted)
                .isEmpty();
    }

    @Test
    void anotherItinerarysBookingDoesNotMakeThisOneAlreadyBooked() {
        FlightItineraryBooked other = new FlightItineraryBooked(FlightItineraryId.of(UUID.randomUUID()),
                "United Airlines", "ZZZZZZ", List.of());
        SpyCommandExecutor executor = new SpyCommandExecutor(other);

        ItineraryEvaluation evaluation = booking(executor)
                .book("request", SAMPLE, Map.of(), ITINERARY, NEW_FLIGHT_ID, NOW);

        assertThat(evaluation.alreadyBooked())
                .isFalse();
        assertThat(evaluation.bookable())
                .isTrue();
    }

    @Test
    void itReportsWhateverModeTheExecutorIsIn() {
        SpyCommandExecutor writable = new SpyCommandExecutor();
        SpyCommandExecutor readOnly = new SpyCommandExecutor();
        readOnly.readOnly = true;

        assertThat(booking(writable).isReadOnly())
                .as("writable executor")
                .isFalse();
        assertThat(booking(readOnly).isReadOnly())
                .as("read-only executor")
                .isTrue();
    }

    private static FlightItineraryBooking booking(SpyCommandExecutor executor) {
        return new FlightItineraryBooking(executor, new AirportZoneResolver(), new LiveScheduledLegs(executor));
    }

    /** A spy: serves a fixed log for decisions, and records what it was asked to write. */
    private static final class SpyCommandExecutor extends CommandExecutor {
        private final List<StoredEvent> log;
        private final List<Event> emitted = new ArrayList<>();
        private UUID commandId;
        private BookFlightItineraryCommand command;

        SpyCommandExecutor(Event... existing) {
            super(null, null);
            this.log = Stream.of(existing)
                    .map(e -> new StoredEvent(1, e.getClass(), UUID.randomUUID(),
                            Instant.parse("2026-01-01T00:00:00Z"), e, UUID.randomUUID()))
                    .toList();
        }

        private boolean readOnly;

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        @Override
        public Stream<StoredEvent> eventsForDecision() {
            return log.stream();
        }

        @Override
        public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                        DomainCommand<C> command) {
            this.commandId = commandId;
            this.command = (BookFlightItineraryCommand) command;
            command.execute(context).forEach(emitted::add);
        }
    }
}
