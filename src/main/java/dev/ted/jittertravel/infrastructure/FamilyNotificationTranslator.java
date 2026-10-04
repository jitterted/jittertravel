package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.NotifyFamily;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.NotifiedSubject;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Turns a newly appended batch into notifications for family, on the reactor's worker thread.
 * <p>
 * <strong>It decides what the batch means, because only the batch can say.</strong> One command can
 * append several events, so a pasted itinerary arrives as its legs and an itinerary event in one
 * batch, and that is what lets it be told as one trip instead of one email per leg:
 * <ul>
 *   <li>a {@code FlightItineraryBooked} is one notification, with its legs;</li>
 *   <li>a {@code FlightBooked} that belongs to an itinerary event in the same batch is never told on
 *       its own — folded into the trip's one message, or, in a schedule change, not told at all,
 *       because an added or reinstated leg is not a new trip;</li>
 *   <li>any other {@code FlightBooked} is a flight on its own.</li>
 * </ul>
 * A changed or cancelled flight is silent: family want to know a trip is new, or that one they were
 * told about is off.
 * <p>
 * This is a reactor, never an {@code EventStreamConsumer}, so it cannot be replayed into: a boot
 * replay reaches projectors only, and the next deploy therefore cannot mail the whole history.
 * Every failure is caught and counted here; nothing may escape into the worker and kill it.
 */
public class FamilyNotificationTranslator implements EventReactor {

    private static final Logger log = LoggerFactory.getLogger(FamilyNotificationTranslator.class);

    private final NotifyFamily notifyFamily;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public FamilyNotificationTranslator(NotifyFamily notifyFamily, Clock clock, MeterRegistry meterRegistry) {
        this.notifyFamily = notifyFamily;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void react(List<StoredEvent> events) {
        for (Notification notification : notificationsIn(events)) {
            try {
                notifyFamily.notifyFamily(UUID.randomUUID(), notification.subject(), notification.fact(),
                        notification.legs(), Instant.now(clock));
            } catch (RuntimeException failed) {
                meterRegistry.counter("family.notification.failed").increment();
                log.warn("Family notification about {} was not sent", notification.subject(), failed);
            }
        }
    }

    private List<Notification> notificationsIn(List<StoredEvent> events) {
        Map<FlightId, FlightBooked> booked = new LinkedHashMap<>();
        Set<FlightId> partOfATrip = new HashSet<>();
        for (StoredEvent stored : events) {
            switch (stored.payload()) {
                case FlightBooked flight -> booked.put(flight.flightId(), flight);
                case FlightItineraryBooked trip -> partOfATrip.addAll(trip.flightIds());
                case FlightItineraryChanged change -> partOfATrip.addAll(change.flightIds());
                default -> { /* not a new trip */ }
            }
        }
        Stream<Notification> trips = events.stream()
                .map(StoredEvent::payload)
                .filter(FlightItineraryBooked.class::isInstance)
                .map(FlightItineraryBooked.class::cast)
                .map(trip -> new Notification(NotifiedSubject.itinerary(trip.itineraryId()),
                        NotifiedFact.ITINERARY_BOOKED, legsOf(trip, booked)))
                .filter(notification -> !notification.legs().isEmpty());
        Stream<Notification> singles = booked.values().stream()
                .filter(flight -> !partOfATrip.contains(flight.flightId()))
                .map(flight -> new Notification(NotifiedSubject.flight(flight.flightId()),
                        NotifiedFact.FLIGHT_BOOKED, List.of(flight)));
        return Stream.concat(trips, singles).toList();
    }

    private List<FlightBooked> legsOf(FlightItineraryBooked trip, Map<FlightId, FlightBooked> booked) {
        return trip.flightIds().stream()
                .map(booked::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .toList();
    }

    private record Notification(NotifiedSubject subject, NotifiedFact fact, List<FlightBooked> legs) {
    }
}
