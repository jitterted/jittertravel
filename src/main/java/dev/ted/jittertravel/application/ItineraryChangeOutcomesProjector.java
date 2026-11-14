package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryRebooked;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * What the last change to each itinerary did, counted: the line the banner on {@code /booked-flights}
 * says once a change is recorded ("Rebooking of HE80PP recorded: 1 leg dropped",
 * {@code docs/FlightItineraryRebookingPlan.md}, approved item 2). The redirect carries only the
 * itinerary and an outcome key, never the sentence, so the counts come from here.
 * <p>
 * A change writes its legs' events in the same append as the itinerary event, so they share a
 * command id. Built from events alone (R12).
 */
public class ItineraryChangeOutcomesProjector implements EventStreamConsumer {

    /** How many legs the change added, moved and dropped, and who made it. */
    public record Outcome(ItineraryChangeKind kind, int added, int moved, int dropped) {
    }

    private record Tally(int added, int moved, int dropped) {
        static final Tally NONE = new Tally(0, 0, 0);
    }

    private final Map<UUID, Tally> tallyByCommand = new ConcurrentHashMap<>();
    private final Map<FlightItineraryId, Outcome> lastByItinerary = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case FlightBooked ignored -> tally(stored.commandId(), 1, 0, 0);
                case FlightChanged ignored -> tally(stored.commandId(), 0, 1, 0);
                case FlightCancelled cancelled when cancelled.cause().dropped() -> tally(stored.commandId(), 0, 0, 1);
                case FlightItineraryChanged change ->
                        record(change.itineraryId(), ItineraryChangeKind.SCHEDULE_CHANGE, stored.commandId());
                case FlightItineraryRebooked rebooking ->
                        record(rebooking.itineraryId(), ItineraryChangeKind.REBOOKING, stored.commandId());
                default -> { /* not part of a change */ }
            }
        });
    }

    /** The itinerary's latest change, if it has had one. */
    public Optional<Outcome> lastChange(FlightItineraryId itineraryId) {
        return Optional.ofNullable(lastByItinerary.get(itineraryId));
    }

    private void tally(UUID commandId, int added, int moved, int dropped) {
        tallyByCommand.merge(commandId, new Tally(added, moved, dropped),
                (was, more) -> new Tally(was.added() + more.added(), was.moved() + more.moved(),
                        was.dropped() + more.dropped()));
    }

    private void record(FlightItineraryId itineraryId, ItineraryChangeKind kind, UUID commandId) {
        Tally tally = tallyByCommand.getOrDefault(commandId, Tally.NONE);
        lastByItinerary.put(itineraryId, new Outcome(kind, tally.added(), tally.moved(), tally.dropped()));
        tallyByCommand.remove(commandId);
    }
}
