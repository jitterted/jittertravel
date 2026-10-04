package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CancelFlightItineraryCommand;
import dev.ted.jittertravel.domain.CancelFlightItineraryContext;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelFlightItineraryRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cancels every leg of a booked itinerary at once. Every decision fact is folded from the event
 * stream (R1) — whether the itinerary is live from the itinerary events, and which of its legs are
 * still on the books from {@link LiveScheduledLegs}, the one fold of "live" the write paths share —
 * and the write goes through {@link CommandExecutor}, which appends the legs' cancellations and the
 * itinerary's in one transaction.
 * <p>
 * commandId and {@code now} are captured at the boundary. Here {@code now} is checked as well as
 * recorded: the command refuses once a leg has departed.
 */
public class CancelFlightItinerary {
    private final CommandExecutor commandExecutor;
    private final LiveScheduledLegs liveScheduledLegs;

    public CancelFlightItinerary(CommandExecutor commandExecutor, LiveScheduledLegs liveScheduledLegs) {
        this.commandExecutor = commandExecutor;
        this.liveScheduledLegs = liveScheduledLegs;
    }

    public void cancelItinerary(UUID commandId, CancelFlightItineraryRequest request, Instant now) {
        FlightItineraryId itineraryId = FlightItineraryId.of(request.itineraryId());
        commandExecutor.execute(commandId, request, contextFor(itineraryId, now),
                new CancelFlightItineraryCommand(itineraryId, request.reason(), now));
    }

    /**
     * An explicit loop rather than {@code reduce}: whether the itinerary is live depends on the
     * order of its booking and its cancellation (see {@link CancelFlight}).
     */
    private CancelFlightItineraryContext contextFor(FlightItineraryId itineraryId, Instant now) {
        boolean live = false;
        List<FlightId> members = List.of();
        for (StoredEvent stored : commandExecutor.eventsForDecision().toList()) {
            switch (stored.payload()) {
                case FlightItineraryBooked e when e.itineraryId().equals(itineraryId) -> {
                    live = true;
                    members = e.flightIds();
                }
                case FlightItineraryChanged e when e.itineraryId().equals(itineraryId) -> members = e.flightIds();
                case FlightItineraryCancelled e when e.itineraryId().equals(itineraryId) -> live = false;
                default -> { /* not this itinerary's */ }
            }
        }
        return new CancelFlightItineraryContext(live, liveLegsOf(members), now);
    }

    private List<ScheduledLeg> liveLegsOf(List<FlightId> members) {
        return liveScheduledLegs.fold().legs().stream()
                .filter(leg -> flightIdOf(leg).map(members::contains).orElse(false))
                .toList();
    }

    private static Optional<FlightId> flightIdOf(ScheduledLeg leg) {
        return leg.id() instanceof ScheduledLegId.Flight flight
                ? Optional.of(flight.id())
                : Optional.empty();
    }
}
