package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
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
import dev.ted.jittertravel.domain.FlightItineraryHasDeparted;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelFlightItineraryRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Covers the decision folds: whether the itinerary is live comes from the itinerary events, and
 * which of its legs are still on the books from the flight events — both from the authoritative
 * stream, never a projector (R1).
 */
class CancelFlightItineraryTest {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final FlightItineraryId itinerary = FlightItineraryId.of(UUID.randomUUID());
    private final FlightId out = FlightId.random();
    private final FlightId back = FlightId.random();

    @Test
    void cancelsEveryLegAndTheItineraryStampedWithNow() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 10)),
                stored(2, booked(back, 20)),
                stored(3, itineraryBooked())));

        service(executor).cancelItinerary(UUID.randomUUID(),
                new CancelFlightItineraryRequest(itinerary.id(), "Called off"), NOW);

        assertThat(executor.emittedEvents)
                .containsExactly(new FlightCancelled(out, "Called off", NOW),
                        new FlightCancelled(back, "Called off", NOW),
                        new FlightItineraryCancelled(itinerary, "Called off", NOW));
    }

    @Test
    void aLegCancelledOnItsOwnIsNotCancelledTwice() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 10)),
                stored(2, booked(back, 20)),
                stored(3, itineraryBooked()),
                stored(4, new FlightCancelled(out, "singly", NOW))));

        service(executor).cancelItinerary(UUID.randomUUID(),
                new CancelFlightItineraryRequest(itinerary.id(), ""), NOW);

        assertThat(executor.emittedEvents)
                .containsExactly(new FlightCancelled(back, "", NOW),
                        new FlightItineraryCancelled(itinerary, "", NOW));
    }

    @Test
    void anotherItinerarysLegsAreLeftAlone() {
        FlightId elsewhere = FlightId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 10)),
                stored(2, booked(elsewhere, 12)),
                stored(3, itineraryBooked(out)),
                stored(4, new FlightItineraryBooked(FlightItineraryId.of(UUID.randomUUID()),
                        "United Airlines", "ZZZZZZ", List.of(elsewhere)))));

        service(executor).cancelItinerary(UUID.randomUUID(),
                new CancelFlightItineraryRequest(itinerary.id(), ""), NOW);

        assertThat(executor.emittedEvents)
                .containsExactly(new FlightCancelled(out, "", NOW),
                        new FlightItineraryCancelled(itinerary, "", NOW));
    }

    @Test
    void aLegThatWasRescheduledIsJudgedByItsNewDeparture() {
        // Booked for the 1st (flown), changed to the 10th: still ahead, so the itinerary can go.
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 1)),
                stored(2, itineraryBooked(out)),
                stored(3, new FlightChanged(out, "United Airlines", "UA1", AirportCode.of("SFO"), at(10, 8),
                        AirportCode.of("ORD"), at(10, 11), "Airline schedule change"))));

        service(executor).cancelItinerary(UUID.randomUUID(),
                new CancelFlightItineraryRequest(itinerary.id(), ""), NOW);

        assertThat(executor.emittedEvents)
                .containsExactly(new FlightCancelled(out, "", NOW),
                        new FlightItineraryCancelled(itinerary, "", NOW));
    }

    @Test
    void aLegAddedByAScheduleChangeIsCancelledWithTheRest() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 10)),
                stored(2, itineraryBooked(out)),
                stored(3, booked(back, 20)),
                stored(4, new FlightItineraryChanged(itinerary, List.of(out, back), "Airline schedule change", NOW))));

        service(executor).cancelItinerary(UUID.randomUUID(),
                new CancelFlightItineraryRequest(itinerary.id(), ""), NOW);

        assertThat(executor.emittedEvents)
                .containsExactly(new FlightCancelled(out, "", NOW),
                        new FlightCancelled(back, "", NOW),
                        new FlightItineraryCancelled(itinerary, "", NOW));
    }

    @Test
    void anotherItinerarysScheduleChangeDoesNotChangeThisOnesLegs() {
        FlightId elsewhere = FlightId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 10)),
                stored(2, booked(elsewhere, 12)),
                stored(3, itineraryBooked(out)),
                stored(4, new FlightItineraryChanged(FlightItineraryId.of(UUID.randomUUID()),
                        List.of(elsewhere), "Airline schedule change", NOW))));

        service(executor).cancelItinerary(UUID.randomUUID(),
                new CancelFlightItineraryRequest(itinerary.id(), ""), NOW);

        assertThat(executor.emittedEvents)
                .containsExactly(new FlightCancelled(out, "", NOW),
                        new FlightItineraryCancelled(itinerary, "", NOW));
    }

    @Test
    void aDepartedLegRefusesTheWholeItinerary() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 1)),
                stored(2, booked(back, 20)),
                stored(3, itineraryBooked())));

        assertThatExceptionOfType(FlightItineraryHasDeparted.class)
                .isThrownBy(() -> service(executor).cancelItinerary(UUID.randomUUID(),
                        new CancelFlightItineraryRequest(itinerary.id(), ""), NOW));
    }

    @Test
    void aDepartedLegThatWasCancelledOnItsOwnNoLongerBlocksTheRest() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 1)),
                stored(2, booked(back, 20)),
                stored(3, itineraryBooked()),
                stored(4, new FlightCancelled(out, "never flew", NOW))));

        service(executor).cancelItinerary(UUID.randomUUID(),
                new CancelFlightItineraryRequest(itinerary.id(), ""), NOW);

        assertThat(executor.emittedEvents)
                .containsExactly(new FlightCancelled(back, "", NOW),
                        new FlightItineraryCancelled(itinerary, "", NOW));
    }

    @Test
    void unknownItineraryIsRefused() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 10))));

        assertThatExceptionOfType(FlightItineraryNotFound.class)
                .isThrownBy(() -> service(executor).cancelItinerary(UUID.randomUUID(),
                        new CancelFlightItineraryRequest(UUID.randomUUID(), ""), NOW));
    }

    @Test
    void itineraryAlreadyCancelledIsRefused() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                stored(1, booked(out, 10)),
                stored(2, itineraryBooked(out)),
                stored(3, new FlightCancelled(out, "first time", NOW)),
                stored(4, new FlightItineraryCancelled(itinerary, "first time", NOW))));

        assertThatExceptionOfType(FlightItineraryNotFound.class)
                .isThrownBy(() -> service(executor).cancelItinerary(UUID.randomUUID(),
                        new CancelFlightItineraryRequest(itinerary.id(), "again"), NOW));
    }

    private static CancelFlightItinerary service(RecordingCommandExecutor executor) {
        return new CancelFlightItinerary(executor, new LiveScheduledLegs(executor));
    }

    private FlightItineraryBooked itineraryBooked() {
        return itineraryBooked(out, back);
    }

    private FlightItineraryBooked itineraryBooked(FlightId... flights) {
        return new FlightItineraryBooked(itinerary, "United Airlines", "MD7LKB", List.of(flights));
    }

    private static FlightBooked booked(FlightId flightId, int dayOfOctober) {
        return new FlightBooked(flightId, "United Airlines", "UA1",
                AirportCode.of("SFO"), at(dayOfOctober, 8), AirportCode.of("ORD"), at(dayOfOctober, 11));
    }

    private static ZonedTimestamp at(int dayOfOctober, int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, dayOfOctober, hour, 0), CHICAGO);
    }

    private static StoredEvent stored(long sequence, Event payload) {
        return new StoredEvent(sequence, payload.getClass(), UUID.randomUUID(),
                Instant.now(), payload, UUID.randomUUID());
    }

    /** A spy: records what the service emitted, running the command against the folded context. */
    private static final class RecordingCommandExecutor extends CommandExecutor {
        private final List<StoredEvent> events;
        private List<Event> emittedEvents = new ArrayList<>();

        RecordingCommandExecutor(Stream<StoredEvent> events) {
            super(null, null);
            this.events = events.toList();
        }

        @Override
        public Stream<StoredEvent> eventsForDecision() {
            return events.stream();
        }

        @Override
        public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                        DomainCommand<C> command) {
            this.emittedEvents = command.execute(context).map(Event.class::cast).toList();
        }
    }
}
