package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.NotifyFamily;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceDatesChanged;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.NotifiedSubject;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * A batch is one append. One command can append several events, so the batch — not the single
 * event — is what says whether legs arrived alone or as a trip.
 */
@ExtendWith(MockitoExtension.class)
class FamilyNotificationActuatorTest {

    private static final Instant NOW = Instant.parse("2026-10-05T17:00:00Z");
    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    @Mock NotifyFamily notifyFamily;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private final FlightBooked first = leg("UA2091", 18);
    private final FlightBooked second = leg("UA3509", 19);
    private final FlightBooked third = leg("UA3510", 25);

    private FamilyNotificationActuator actuatorFor() {
        return new FamilyNotificationActuator(notifyFamily, Clock.fixed(NOW, ZoneOffset.UTC), meters);
    }

    @Test
    void aFlightBookedOnItsOwnNotifiesAsAFlight() {
        actuatorFor().react(batch(first));

        verify(notifyFamily).notifyFamily(any(UUID.class), eq(NotifiedSubject.flight(first.flightId())),
                eq(NotifiedFact.FLIGHT_BOOKED), eq(List.of(first)), eq(NOW));
    }

    @Test
    void aPastedItineraryNotifiesOnceForTheWholeTripNotOncePerLeg() {
        FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());

        actuatorFor().react(batch(first, second, third,
                new FlightItineraryBooked(trip, "United Airlines", "MD7LKB",
                        List.of(first.flightId(), second.flightId(), third.flightId()))));

        verify(notifyFamily, times(1)).notifyFamily(any(), any(), any(), any(), any());
        verify(notifyFamily).notifyFamily(any(UUID.class), eq(NotifiedSubject.itinerary(trip)),
                eq(NotifiedFact.ITINERARY_BOOKED), eq(List.of(first, second, third)), eq(NOW));
    }

    @Test
    void anItineraryWhoseLegsAreNotInTheBatchHasNothingToSayAndNotifiesNobody() {
        // Not reachable from the real command, which appends the legs with the itinerary, but a
        // trip email with no flights in it would be worse than none.
        FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());

        actuatorFor().react(batch(new FlightItineraryBooked(trip, "United Airlines", "MD7LKB",
                List.of(first.flightId()))));

        verifyNoInteractions(notifyFamily);
    }

    @Test
    void theLegsOfATripAreOrderedByDepartureWhateverOrderTheItineraryListsThem() {
        FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());

        actuatorFor().react(batch(first, second, third,
                new FlightItineraryBooked(trip, "United Airlines", "MD7LKB",
                        List.of(third.flightId(), first.flightId(), second.flightId()))));

        verify(notifyFamily).notifyFamily(any(), any(), any(), eq(List.of(first, second, third)), any());
    }

    /**
     * A schedule change that adds a leg, or reinstates one the airline dropped, writes a new
     * FlightBooked — but family want to hear about new trips and cancelled ones, and this is neither.
     */
    @Test
    void aFlightBookedByAScheduleChangeNotifiesNobody() {
        FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());

        actuatorFor().react(batch(second,
                new FlightItineraryChanged(trip, List.of(first.flightId(), second.flightId()),
                        "Airline schedule change", NOW)));

        verifyNoInteractions(notifyFamily);
    }

    @Test
    void eventsThatAreNotNewTripsAreIgnored() {
        actuatorFor().react(batch(new FlightCancelled(first.flightId(), "", NOW)));

        verifyNoInteractions(notifyFamily);
    }

    /** One dropped leg is often a rebooking, not a trip that is off (Ted, 2026-10-05). */
    @Test
    void cancellingOneFlightOnItsOwnTellsNobody() {
        actuatorFor().react(batch(new FlightCancelled(first.flightId(), "Rebooked", NOW)));

        verifyNoInteractions(notifyFamily);
    }

    @Test
    void cancellingAWholeItineraryTellsFamilyOnceWithTheLegsThatWereCancelledWithIt() {
        FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());

        actuatorFor().react(batch(
                new FlightCancelled(first.flightId(), "Rebooked", NOW),
                new FlightCancelled(second.flightId(), "Rebooked", NOW),
                new FlightItineraryCancelled(trip, "Rebooked", NOW)));

        verify(notifyFamily, times(1)).notifyOfCancelledItinerary(any(UUID.class), eq(trip),
                eq(Set.of(first.flightId(), second.flightId())), eq(NOW));
        verifyNoMoreInteractions(notifyFamily);
    }

    @Test
    void aFailureNotifyingACancellationIsCountedAndDoesNotEscape() {
        FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());
        given(notifyFamily.notifyOfCancelledItinerary(any(), any(), any(), any()))
                .willThrow(new IllegalStateException("Brevo returned 502"));

        actuatorFor().react(batch(
                new FlightCancelled(first.flightId(), "", NOW), new FlightItineraryCancelled(trip, "", NOW)));

        assertThat(meters.counter("family.notification.failed").count())
                .isEqualTo(1.0);
    }

    @Test
    void aFailureOnOneNotificationIsCountedAndDoesNotStopTheNext() {
        given(notifyFamily.notifyFamily(any(), eq(NotifiedSubject.flight(first.flightId())), any(), any(), any()))
                .willThrow(new IllegalStateException("Brevo returned 502"));

        actuatorFor().react(batch(first, second));

        verify(notifyFamily).notifyFamily(any(), eq(NotifiedSubject.flight(second.flightId())), any(), any(), any());
        assertThat(meters.counter("family.notification.failed").count())
                .isEqualTo(1.0);
    }

    @Test
    void everyNotificationGetsItsOwnCommandId() {
        actuatorFor().react(batch(first, second));

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(notifyFamily, times(2)).notifyFamily(ids.capture(), any(), any(), any(), any());
        assertThat(ids.getAllValues())
                .doesNotHaveDuplicates();
    }

    // ---- conferences: the five events that move a commitment ----------------------------------

    private final ConferenceId socrates = ConferenceId.random();

    @Test
    void eachOfTheFiveCommitmentEventsAsksTheServiceAboutItsConference() {
        List<Event> triggers = List.of(
                new ConferenceAttendanceConfirmed(socrates, AttendanceBasis.TICKET_PURCHASED, NOW),
                new ConferenceAttendanceDeclined(socrates, "", NOW),
                new ConferenceCancelled(socrates, ""),
                new TalkAccepted(socrates, NOW),
                new TalkRejected(socrates, NOW));

        for (Event trigger : triggers) {
            actuatorFor().react(batch(trigger));
        }

        verify(notifyFamily, times(5)).notifyOfConference(any(UUID.class), eq(socrates), eq(NOW));
        verifyNoMoreInteractions(notifyFamily);
    }

    @Test
    void severalEventsForOneConferenceInOneBatchAskOnceAndEachConferenceGetsItsOwnCommandId() {
        ConferenceId devoxx = ConferenceId.random();

        actuatorFor().react(batch(
                new TalkAccepted(socrates, NOW),
                new ConferenceAttendanceConfirmed(socrates, AttendanceBasis.SPEAKING_ACCEPTED, NOW),
                new TalkAccepted(devoxx, NOW)));

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(notifyFamily).notifyOfConference(ids.capture(), eq(socrates), eq(NOW));
        verify(notifyFamily).notifyOfConference(ids.capture(), eq(devoxx), eq(NOW));
        assertThat(ids.getAllValues())
                .doesNotHaveDuplicates();
        verifyNoMoreInteractions(notifyFamily);
    }

    @Test
    void conferenceEventsThatMoveOnlyTheTalkOrThePlanAreSilent() {
        actuatorFor().react(batch(
                new TalkSubmitted(socrates, NOW),
                new TalkWithdrawn(socrates, NOW),
                new InvitedToSpeak(socrates, NOW),
                new ConferenceDatesChanged(socrates,
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 8, 17, 9, 0), PACIFIC),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 8, 20, 9, 0), PACIFIC))));

        verifyNoInteractions(notifyFamily);
    }

    @Test
    void aFailureAboutAConferenceIsCountedAndDoesNotStopTheNext() {
        ConferenceId devoxx = ConferenceId.random();
        given(notifyFamily.notifyOfConference(any(), eq(socrates), any()))
                .willThrow(new IllegalStateException("Brevo returned 502"));

        actuatorFor().react(batch(new TalkAccepted(socrates, NOW), new TalkAccepted(devoxx, NOW)));

        verify(notifyFamily).notifyOfConference(any(), eq(devoxx), any());
        assertThat(meters.counter("family.notification.failed").count())
                .isEqualTo(1.0);
    }

    private List<StoredEvent> batch(Event... events) {
        long[] sequence = {0};
        return Stream.of(events)
                .map(event -> new StoredEvent(++sequence[0], event.getClass(), UUID.randomUUID(), NOW, event,
                        UUID.randomUUID()))
                .toList();
    }

    private FlightBooked leg(String number, int day) {
        return new FlightBooked(FlightId.random(), "United Airlines", number,
                AirportCode.of("SFO"), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, day, 6, 10), PACIFIC),
                AirportCode.of("ORD"), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, day, 12, 45), PACIFIC));
    }
}
