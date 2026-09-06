package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.domain.ScheduledLegs;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainCancelled;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds every live flight and train out of the authoritative event stream, for the four write paths
 * that must refuse a journey colliding with one.
 * <p>
 * <strong>One fold, shared by all four</strong> — book and change, train and flight. They all want
 * the same facts, and the collision question is deliberately kind-blind (a flight overlapping a
 * train is the same impossibility), so four near-identical folds would be four chances to disagree
 * about what "already booked" means.
 * <p>
 * <strong>From the stream, never from {@code ScheduleGapProjector}</strong> (R1): a command must not
 * decide from a projection. It is also the only source that cannot be a batch stale, which matters
 * here — deciding against a projector that has not yet seen the leg booked a second ago would let
 * exactly the duplicate this refuses through.
 * <p>
 * <strong>Live means cancellations applied.</strong> A cancelled trip must not block a booking; that
 * is how Ted resolves a collision, and a fold that ignored {@link TrainCancelled} would leave him
 * unable to re-enter the trip he just corrected. Flights have no cancellation event yet, so there is
 * nothing to apply for them — when Cancel Flight ships it belongs here, and
 * {@code LiveScheduledLegsTest} is where that shows up.
 */
public class LiveScheduledLegs {

    private final CommandExecutor commandExecutor;

    public LiveScheduledLegs(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public ScheduledLegs fold() {
        // Keyed by id so a *Changed replaces the leg it restates rather than adding a second one,
        // which would collide with itself on the next edit. Insertion-ordered so the leg reported
        // as blocking is the earliest booked, not an arbitrary one.
        Map<ScheduledLegId, ScheduledLeg> live = new LinkedHashMap<>();
        commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .forEach(event -> apply(live, event));
        return new ScheduledLegs(List.copyOf(live.values()));
    }

    private static void apply(Map<ScheduledLegId, ScheduledLeg> live, Object event) {
        switch (event) {
            case FlightBooked e -> put(live, new ScheduledLegId.Flight(e.flightId()),
                    e.departureDateTime(), e.arrivalDateTime());
            case FlightChanged e -> put(live, new ScheduledLegId.Flight(e.flightId()),
                    e.departureDateTime(), e.arrivalDateTime());
            case TrainBooked e -> put(live, new ScheduledLegId.Train(e.tripId()),
                    e.departureDateTime(), e.arrivalDateTime());
            case TrainChanged e -> put(live, new ScheduledLegId.Train(e.tripId()),
                    e.departureDateTime(), e.arrivalDateTime());
            case TrainCancelled e -> live.remove(new ScheduledLegId.Train(e.tripId()));
            default -> { /* not a scheduled leg */ }
        }
    }

    private static void put(Map<ScheduledLegId, ScheduledLeg> live, ScheduledLegId id,
                            ZonedTimestamp departure, ZonedTimestamp arrival) {
        live.put(id, new ScheduledLeg(id, departure, arrival));
    }
}
