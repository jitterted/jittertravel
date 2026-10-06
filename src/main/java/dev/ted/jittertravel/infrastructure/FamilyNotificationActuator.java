package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.NotifyFamily;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.NotifiedSubject;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
public class FamilyNotificationActuator implements EventReactor {

    private static final Logger log = LoggerFactory.getLogger(FamilyNotificationActuator.class);

    private final NotifyFamily notifyFamily;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public FamilyNotificationActuator(NotifyFamily notifyFamily, Clock clock, MeterRegistry meterRegistry) {
        this.notifyFamily = notifyFamily;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void react(List<StoredEvent> events) {
        Instant now = Instant.now(clock);
        for (Notification notification : notificationsIn(events)) {
            try {
                notifyFamily.notifyFamily(UUID.randomUUID(), notification.subject(), notification.fact(),
                        notification.legs(), now);
            } catch (RuntimeException failed) {
                countFailure(notification.subject(), failed);
            }
        }
        for (Cancellation cancellation : cancellationsIn(events)) {
            try {
                notifyFamily.notifyOfCancelledItinerary(UUID.randomUUID(), cancellation.itinerary(),
                        cancellation.flights(), now);
            } catch (RuntimeException failed) {
                countFailure(NotifiedSubject.itinerary(cancellation.itinerary()), failed);
            }
        }
        for (ConferenceId conference : conferencesMovedIn(events)) {
            try {
                notifyFamily.notifyOfConference(UUID.randomUUID(), conference, now);
            } catch (RuntimeException failed) {
                countFailure(NotifiedSubject.conference(conference), failed);
            }
        }
    }

    /**
     * Conferences whose commitment may have moved in this batch: the five events that move
     * {@code AttendanceCommitment}. Each conference once, however many of them arrived together, and
     * the service decides from the folded stream whether that changed anything family were told.
     * The other conference events are silent: a submitted or withdrawn talk and an invitation move
     * only the speaking axis, and planning or re-dating a conference is not a commitment to go.
     */
    private Set<ConferenceId> conferencesMovedIn(List<StoredEvent> events) {
        Set<ConferenceId> moved = new LinkedHashSet<>();
        for (StoredEvent stored : events) {
            switch (stored.payload()) {
                case ConferenceAttendanceConfirmed event -> moved.add(event.conferenceId());
                case ConferenceAttendanceDeclined event -> moved.add(event.conferenceId());
                case ConferenceCancelled event -> moved.add(event.conferenceId());
                case TalkAccepted event -> moved.add(event.conferenceId());
                case TalkRejected event -> moved.add(event.conferenceId());
                default -> { /* does not move a commitment */ }
            }
        }
        return moved;
    }

    private void countFailure(NotifiedSubject subject, RuntimeException failed) {
        meterRegistry.counter("family.notification.failed").increment();
        log.warn("Family notification about {} was not sent", subject, failed);
    }

    /**
     * A whole itinerary cancelled: the itinerary event, and the flights cancelled in the same append.
     * A {@code FlightCancelled} on its own is not here, because one dropped leg is often a rebooking
     * and not a trip that is off (Ted, 2026-10-05).
     */
    private List<Cancellation> cancellationsIn(List<StoredEvent> events) {
        Set<FlightId> cancelledFlights = events.stream()
                .map(StoredEvent::payload)
                .filter(FlightCancelled.class::isInstance)
                .map(FlightCancelled.class::cast)
                .map(FlightCancelled::flightId)
                .collect(Collectors.toSet());
        return events.stream()
                .map(StoredEvent::payload)
                .filter(FlightItineraryCancelled.class::isInstance)
                .map(FlightItineraryCancelled.class::cast)
                .map(cancelled -> new Cancellation(cancelled.itineraryId(), cancelledFlights))
                .toList();
    }

    private record Cancellation(FlightItineraryId itinerary, Set<FlightId> flights) {
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
