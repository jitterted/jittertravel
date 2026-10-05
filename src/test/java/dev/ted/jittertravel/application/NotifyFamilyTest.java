package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FamilyNotified;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.NotifiedSubject;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.FamilyNotificationMessages;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The service decides whether family need telling and, if so, sends and records it. The executor is
 * the real one over a mocked persister and store, so the write-ahead order is the real order; the
 * Brevo client is a stub for {@code configured()} and a spy for what was sent.
 */
@ExtendWith(MockitoExtension.class)
class NotifyFamilyTest {

    private static final Instant NOW = Instant.parse("2026-10-05T17:00:00Z");
    private static final UUID COMMAND_ID = UUID.randomUUID();
    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");

    @Mock PostgresPersister persister;
    @Mock EventStore eventStore;
    @Mock BrevoEmailClient brevo;

    private final FlightBooked sfoOrd = new FlightBooked(FlightId.random(), "United Airlines", "UA2091",
            AirportCode.of("SFO"), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 6, 10), PACIFIC),
            AirportCode.of("ORD"), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 12, 45), CHICAGO));
    private final NotifiedSubject subject = NotifiedSubject.flight(sfoOrd.flightId());

    private NotifyFamily service(boolean enabled) {
        return new NotifyFamily(new CommandExecutor(persister, eventStore), brevo,
                new FamilyNotificationMessages(new StaticAirportCityResolver(), ""), enabled);
    }

    private void configured() {
        given(brevo.configured()).willReturn(true);
    }

    private void history(Event... events) {
        long[] sequence = {0};
        given(eventStore.findAll()).willAnswer(_ -> Stream.of(events)
                .map(event -> new StoredEvent(++sequence[0], event.getClass(), UUID.randomUUID(), NOW, event,
                        UUID.randomUUID())));
    }

    @Test
    void aDisabledNotifierWritesNoCommandRowAndSendsNothing() {
        NotifyFamily.Outcome outcome = service(false)
                .notifyFamily(COMMAND_ID, subject, NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd), NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.NOT_ENABLED);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void anUnconfiguredNotifierWritesNoCommandRowAndSendsNothing() {
        given(brevo.configured()).willReturn(false);

        NotifyFamily.Outcome outcome = service(true)
                .notifyFamily(COMMAND_ID, subject, NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd), NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.NOT_CONFIGURED);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void sendsTheMessageThenRecordsWhatFamilyWereTold() {
        configured();
        history();

        NotifyFamily.Outcome outcome = service(true)
                .notifyFamily(COMMAND_ID, subject, NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd), NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.SENT);
        InOrder inOrder = inOrder(persister, brevo, eventStore);
        inOrder.verify(persister).saveCommand(COMMAND_ID,
                new NotifyFamilyCommand(subject, NotifiedFact.FLIGHT_BOOKED, NOW));
        ArgumentCaptor<FamilyMessage> sent = ArgumentCaptor.forClass(FamilyMessage.class);
        inOrder.verify(brevo).send(sent.capture());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Stream<? extends Event>> appended = ArgumentCaptor.forClass(Stream.class);
        inOrder.verify(eventStore).append(appended.capture(), eq(COMMAND_ID));

        assertThat(sent.getValue().subject())
                .isEqualTo("(JitterTravel) Ted booked a new flight: SFO → ORD");
        assertThat(appended.getValue().map(Event.class::cast).toList())
                .containsExactly(new FamilyNotified(subject, NotifiedFact.FLIGHT_BOOKED, NOW));
    }

    @Test
    void aFailedSendLeavesAFailedSendRowAndNoEvent() {
        configured();
        history();
        willThrow(new IllegalStateException("Brevo returned 502")).given(brevo).send(any());

        assertThatThrownBy(() -> service(true)
                .notifyFamily(COMMAND_ID, subject, NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd), NOW))
                .hasMessage("Brevo returned 502");

        verify(persister).markCommandFailed(COMMAND_ID, "FAILED_SEND", "Brevo returned 502");
        verify(eventStore, never()).append(any(), any());
    }

    @Test
    void whatFamilyWereAlreadyToldIsNotSaidAgain() {
        configured();
        history(new FamilyNotified(subject, NotifiedFact.FLIGHT_BOOKED, NOW.minusSeconds(60)));

        NotifyFamily.Outcome outcome = service(true)
                .notifyFamily(COMMAND_ID, subject, NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd), NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.ALREADY_TOLD);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void aNotificationAboutSomethingElseDoesNotSuppressThisOne() {
        configured();
        history(new FamilyNotified(NotifiedSubject.flight(FlightId.random()), NotifiedFact.FLIGHT_BOOKED,
                NOW.minusSeconds(60)));

        NotifyFamily.Outcome outcome = service(true)
                .notifyFamily(COMMAND_ID, subject, NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd), NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.SENT);
    }

    @Test
    void aTripIsNotSuppressedByTheSameIdBeingToldAsADifferentKind() {
        configured();
        FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());
        history(new FamilyNotified(NotifiedSubject.flight(FlightId.of(trip.id())), NotifiedFact.FLIGHT_BOOKED,
                NOW.minusSeconds(60)));

        NotifyFamily.Outcome outcome = service(true).notifyFamily(COMMAND_ID, NotifiedSubject.itinerary(trip),
                NotifiedFact.ITINERARY_BOOKED, List.of(sfoOrd), NOW);

        assertThat(outcome)
                .as("the subject is the kind and the id together")
                .isEqualTo(NotifyFamily.Outcome.SENT);
    }

    // ---- a whole itinerary cancelled -----------------------------------------------------------

    private final FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());
    private final FlightBooked ordSfo = new FlightBooked(FlightId.random(), "United Airlines", "UA2092",
            AirportCode.of("ORD"), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 25, 13, 0), CHICAGO),
            AirportCode.of("SFO"), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 25, 15, 55), PACIFIC));

    private FamilyNotified toldBooked() {
        return new FamilyNotified(NotifiedSubject.itinerary(trip), NotifiedFact.ITINERARY_BOOKED, NOW.minusSeconds(60));
    }

    private NotifyFamily.Outcome cancelled(Set<FlightId> flights) {
        return service(true).notifyOfCancelledItinerary(COMMAND_ID, trip, flights, NOW);
    }

    @Test
    void cancellingATripFamilyWereToldAboutTellsThemItIsOffAndRecordsIt() {
        configured();
        history(sfoOrd, ordSfo, toldBooked());

        NotifyFamily.Outcome outcome = cancelled(Set.of(sfoOrd.flightId(), ordSfo.flightId()));

        assertThat(outcome).isEqualTo(NotifyFamily.Outcome.SENT);
        ArgumentCaptor<FamilyMessage> sent = ArgumentCaptor.forClass(FamilyMessage.class);
        InOrder inOrder = inOrder(persister, brevo, eventStore);
        inOrder.verify(persister).saveCommand(COMMAND_ID,
                new NotifyFamilyCommand(NotifiedSubject.itinerary(trip), NotifiedFact.ITINERARY_CANCELLED, NOW));
        inOrder.verify(brevo).send(sent.capture());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Stream<? extends Event>> appended = ArgumentCaptor.forClass(Stream.class);
        inOrder.verify(eventStore).append(appended.capture(), eq(COMMAND_ID));

        assertThat(sent.getValue().subject()).isEqualTo("(JitterTravel) Ted cancelled a trip: SFO → ORD → SFO");
        assertThat(appended.getValue().map(Event.class::cast).toList())
                .containsExactly(new FamilyNotified(NotifiedSubject.itinerary(trip),
                        NotifiedFact.ITINERARY_CANCELLED, NOW));
    }

    @Test
    void aTripFamilyWereNeverToldAboutIsCancelledInSilence() {
        configured();
        history(sfoOrd, ordSfo);

        NotifyFamily.Outcome outcome = cancelled(Set.of(sfoOrd.flightId(), ordSfo.flightId()));

        assertThat(outcome)
                .as("telling family a trip is off when they never heard it was on would be news from nowhere")
                .isEqualTo(NotifyFamily.Outcome.NEVER_TOLD);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void aTripFamilyWereAlreadyToldIsCancelledIsNotToldAgain() {
        configured();
        history(sfoOrd, ordSfo, toldBooked(),
                new FamilyNotified(NotifiedSubject.itinerary(trip), NotifiedFact.ITINERARY_CANCELLED,
                        NOW.minusSeconds(30)));

        assertThat(cancelled(Set.of(sfoOrd.flightId(), ordSfo.flightId())))
                .isEqualTo(NotifyFamily.Outcome.ALREADY_TOLD);
        verify(brevo, never()).send(any());
    }

    @Test
    void theMessageDescribesOnlyTheLegsThisCancellationCancelledAtTheirCurrentTimes() {
        configured();
        FlightChanged movedByHand = new FlightChanged(sfoOrd.flightId(), "United Airlines", "UA2091",
                AirportCode.of("SFO"), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 7, 10), PACIFIC),
                AirportCode.of("ORD"), ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 12, 45), CHICAGO),
                "Edited");
        history(sfoOrd, ordSfo, movedByHand, toldBooked());

        cancelled(Set.of(sfoOrd.flightId()));

        ArgumentCaptor<FamilyMessage> sent = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(brevo).send(sent.capture());
        assertThat(sent.getValue().subject()).isEqualTo("(JitterTravel) Ted cancelled a trip: SFO → ORD");
        assertThat(sent.getValue().textContent())
                .contains("Sun 18 Oct 2026, 7:10 AM (SFO)")
                .doesNotContain("6:10 AM")
                .doesNotContain("UA2092");
    }

    @Test
    void cancellingFlightsTheStreamDoesNotKnowHasNothingToDescribe() {
        configured();
        history(toldBooked());

        assertThat(cancelled(Set.of(FlightId.random())))
                .isEqualTo(NotifyFamily.Outcome.NO_LEGS);
        verify(brevo, never()).send(any());
    }

    @Test
    void aDisabledOrUnconfiguredNotifierSaysNothingAboutACancellationEither() {
        assertThat(new NotifyFamily(new CommandExecutor(persister, eventStore), brevo,
                new FamilyNotificationMessages(new StaticAirportCityResolver(), ""), false)
                .notifyOfCancelledItinerary(COMMAND_ID, trip, Set.of(sfoOrd.flightId()), NOW))
                .isEqualTo(NotifyFamily.Outcome.NOT_ENABLED);
        given(brevo.configured()).willReturn(false);
        assertThat(cancelled(Set.of(sfoOrd.flightId())))
                .isEqualTo(NotifyFamily.Outcome.NOT_CONFIGURED);
        verify(persister, never()).saveCommand(any(), any());
    }

    @Test
    void readOnlyModeRefusesBeforeAnythingIsWrittenOrSent() {
        configured();
        history();
        given(eventStore.isReadOnly()).willReturn(true);

        assertThatThrownBy(() -> service(true)
                .notifyFamily(COMMAND_ID, subject, NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd), NOW))
                .isInstanceOf(ReadOnlyModeException.class);

        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }
}
