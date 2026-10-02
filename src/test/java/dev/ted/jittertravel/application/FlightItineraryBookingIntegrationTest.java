package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real parser, command, {@code CommandExecutor}, {@code EventStore}, Postgres and the real
 * {@code /booked-flights} projector. {@code FlightItineraryBookingTest} proves what the command
 * emits; only this proves the claim the design rests on — that the legs land together or not at all
 * — because that depends on what the executor and the store do with a batch.
 */
@Tag("spring")
@SpringBootTest
class FlightItineraryBookingIntegrationTest extends AbstractTestcontainerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final Supplier<FlightId> NEW_FLIGHT_ID = () -> FlightId.of(UUID.randomUUID());

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
            """;

    @Autowired FlightItineraryBooking itineraryBooking;
    @Autowired CommandExecutor commandExecutor;
    @Autowired BookedFlightsProjector bookedFlights;
    @Autowired PostgresPersister persister;

    @Test
    void aCleanPasteLandsAsBothLegsAndTheItineraryUnderOneCommand() {
        FlightItineraryId itinerary = newItinerary();

        ItineraryEvaluation evaluation = book(itinerary, SAMPLE);

        assertThat(evaluation.bookable())
                .isTrue();
        assertThat(persister.countEvents())
                .as("two legs and the itinerary")
                .isEqualTo(3);
        assertThat(persister.countCommands("SUCCEEDED"))
                .as("one command, however many events")
                .isEqualTo(1);
        assertThat(persister.countCommands(""))
                .isEqualTo(1);
        List<StoredEvent> stored = commandExecutor.eventsForDecision().toList();
        assertThat(stored)
                .extracting(StoredEvent::commandId)
                .as("every event is attributed to the itinerary's command")
                .containsOnly(itinerary.id());
        assertThat(stored.getLast().payload())
                .isInstanceOf(FlightItineraryBooked.class);
    }

    @Test
    void theBookedLegsAppearOnTheFlightsListThroughTheRealProjector() {
        FlightItineraryId itinerary = newItinerary();
        book(itinerary, SAMPLE);

        // Scoped to this itinerary's own flights: the projector is not reset between tests (an open
        // item in CLAUDE.md), so asserting on the whole list would depend on what ran before.
        assertThat(bookedFlights.views(TimeView.ALL, NOW))
                .filteredOn(view -> flightsOf(itinerary).contains(view.flightId()))
                .extracting(BookedFlightView::flightNumber)
                .containsExactlyInAnyOrder("UA2091", "UA3509");
    }

    @Test
    void aRefusedPasteWritesNoCommandAndNoEvents() {
        book(newItinerary(), SAMPLE);
        int eventsAfterFirst = persister.countEvents();
        int commandsAfterFirst = persister.countCommands("");
        // The same flights again under a new itinerary id: every leg now collides with a booked one.
        ItineraryEvaluation second = book(newItinerary(), SAMPLE);

        assertThat(second.bookable())
                .isFalse();
        assertThat(second.legs())
                .as("each leg collides with the flight booked first")
                .allMatch(leg -> leg.refusal() instanceof OverlappingLegRefused);
        assertThat(persister.countEvents())
                .as("not even a partial booking")
                .isEqualTo(eventsAfterFirst);
        assertThat(persister.countCommands(""))
                .as("a refusal is found by the dry run, before anything is logged")
                .isEqualTo(commandsAfterFirst);
    }

    /** The double-click, or the browser's resubmit: the same form, the same itinerary id, twice. */
    @Test
    void submittingTheSameFormTwiceBooksItOnceAndSaysWhyTheSecondWasNotBooked() {
        FlightItineraryId itinerary = newItinerary();
        book(itinerary, SAMPLE);

        ItineraryEvaluation again = book(itinerary, SAMPLE);

        assertThat(again.alreadyBooked())
                .as("said as what it is, not as a pile of overlaps with itself")
                .isTrue();
        assertThat(again.bookable())
                .isFalse();
        assertThat(persister.countEvents())
                .isEqualTo(3);
        assertThat(persister.countCommands(""))
                .isEqualTo(1);
    }

    /**
     * The one a stale tab can reach even though nothing overlaps: the trip was booked and then its
     * flights cancelled, and the old form is submitted again. The dry run alone would be clean and
     * the reused command id would meet the write-ahead log's primary key, so the id is checked
     * first, from the event stream.
     */
    @Test
    void resubmittingAStaleFormAfterTheFlightsWereCancelledIsAlreadyBookedNotAnError() {
        FlightItineraryId itinerary = newItinerary();
        book(itinerary, SAMPLE);
        cancelFlightsOf(itinerary);
        int eventsBefore = persister.countEvents();
        int commandsBefore = persister.countCommands("");

        assertThat(book(itinerary, SAMPLE).alreadyBooked())
                .isTrue();

        assertThat(persister.countEvents())
                .isEqualTo(eventsBefore);
        assertThat(persister.countCommands(""))
                .isEqualTo(commandsBefore);
    }

    private ItineraryEvaluation book(FlightItineraryId itinerary, String pasted) {
        return itineraryBooking.book("request", pasted, Map.of(), itinerary, NEW_FLIGHT_ID, NOW);
    }

    private void cancelFlightsOf(FlightItineraryId itinerary) {
        commandExecutor.appendEvents(UUID.randomUUID(), "cancel the itinerary's flights, for the test",
                flightsOf(itinerary).stream().map(id -> new FlightCancelled(id, "", NOW)));
    }

    /** From the event stream, the one source this test trusts to be reset between tests. */
    private List<FlightId> flightsOf(FlightItineraryId itinerary) {
        return commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .filter(FlightItineraryBooked.class::isInstance)
                .map(FlightItineraryBooked.class::cast)
                .filter(booked -> booked.itineraryId().equals(itinerary))
                .flatMap(booked -> booked.flightIds().stream())
                .toList();
    }

    private static FlightItineraryId newItinerary() {
        return FlightItineraryId.of(UUID.randomUUID());
    }
}
