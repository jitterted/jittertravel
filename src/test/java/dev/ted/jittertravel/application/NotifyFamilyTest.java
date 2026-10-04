package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FamilyNotified;
import dev.ted.jittertravel.domain.FlightBooked;
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
                new FamilyNotificationMessages(new StaticAirportCityResolver()), enabled);
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
                .isEqualTo("Ted booked a flight: SFO → ORD");
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
