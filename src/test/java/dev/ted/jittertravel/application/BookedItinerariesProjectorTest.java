package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BookedItinerariesProjectorTest {

    private final BookedItinerariesProjector projector = new BookedItinerariesProjector();
    private final FlightItineraryId itinerary = FlightItineraryId.of(UUID.randomUUID());
    private final FlightId out = FlightId.random();
    private final FlightId back = FlightId.random();

    @Test
    void aBookedItineraryIsFoundWithItsCodeAndItsFlights() {
        projector.handle(Stream.of(stored(1, new FlightItineraryBooked(
                itinerary, "United Airlines", "MD7LKB", List.of(out, back)))));

        assertThat(projector.findLive(itinerary))
                .as("Itinerary booked above")
                .hasValue(new BookedItineraryView(itinerary, "United Airlines", "MD7LKB",
                        List.of(out, back), false));
    }

    @Test
    void anItineraryThatWasNeverBookedIsNotFound() {
        assertThat(projector.findLive(itinerary))
                .as("Nothing was booked")
                .isEmpty();
    }

    @Test
    void aCancelledItineraryIsNoLongerLive() {
        projector.handle(Stream.of(
                stored(1, new FlightItineraryBooked(itinerary, "United Airlines", "MD7LKB", List.of(out))),
                stored(2, new FlightItineraryCancelled(itinerary, "", Instant.parse("2026-10-02T12:00:00Z")))));

        assertThat(projector.findLive(itinerary))
                .as("Itinerary that was cancelled")
                .isEmpty();
    }

    @Test
    void cancellingAnotherItineraryLeavesThisOneLive() {
        projector.handle(Stream.of(
                stored(1, new FlightItineraryBooked(itinerary, "United Airlines", "MD7LKB", List.of(out))),
                stored(2, new FlightItineraryCancelled(FlightItineraryId.of(UUID.randomUUID()), "",
                        Instant.parse("2026-10-02T12:00:00Z")))));

        assertThat(projector.findLive(itinerary))
                .as("Itinerary that was not cancelled")
                .isPresent();
    }

    @Test
    void aFlightIsFoundByTheItineraryItWasBookedWithEvenOnceThatItineraryIsCancelled() {
        projector.handle(Stream.of(
                stored(1, new FlightItineraryBooked(itinerary, "United Airlines", "MD7LKB", List.of(out, back))),
                stored(2, new FlightItineraryCancelled(itinerary, "", Instant.parse("2026-10-02T12:00:00Z")))));

        assertThat(projector.findContaining(back))
                .as("a cancelled flight still belongs to its trip")
                .map(BookedItineraryView::confirmationCode)
                .hasValue("MD7LKB");
    }

    @Test
    void aFlightEnteredByHandBelongsToNoItinerary() {
        projector.handle(Stream.of(
                stored(1, new FlightItineraryBooked(itinerary, "United Airlines", "MD7LKB", List.of(out)))));

        assertThat(projector.findContaining(FlightId.random()))
                .as("Flight that no itinerary names")
                .isEmpty();
    }

    private static StoredEvent stored(long sequence, Event payload) {
        return new StoredEvent(sequence, payload.getClass(), UUID.randomUUID(),
                Instant.now(), payload, UUID.randomUUID());
    }
}
