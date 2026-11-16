package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryRebooked;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Ted cancels one flight on its own, with Cancel on its row, and family are always told (Ted,
 * 2026-11-16). The real executor over a mocked persister and store, as in
 * {@link NotifyFamilyItineraryChangeTest}, over HE80PP's legs.
 */
@ExtendWith(MockitoExtension.class)
class NotifyFamilyFlightCancelledTest {

    /** Between the outbound (Nov 7-8) and the return (Nov 26). */
    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final UUID COMMAND_ID = UUID.randomUUID();
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID CANCELLING = UUID.randomUUID();
    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final FlightItineraryId HE80PP = FlightItineraryId.of(UUID.randomUUID());

    @Mock PostgresPersister persister;
    @Mock EventStore eventStore;
    @Mock BrevoEmailClient brevo;

    private final FlightBooked sfoEwr = leg("UA1642", "SFO", 11, 7, 8, 15, PACIFIC, "EWR", 11, 7, 16, 38, NEW_YORK);
    private final FlightBooked ewrBer = leg("UA962", "EWR", 11, 7, 17, 50, NEW_YORK, "BER", 11, 8, 7, 55, BERLIN);
    private final FlightBooked berFra = leg("UA8786", "BER", 11, 26, 10, 40, BERLIN, "FRA", 11, 26, 11, 50, BERLIN);
    private final FlightBooked fraSfo = leg("UA59", "FRA", 11, 26, 13, 30, BERLIN, "SFO", 11, 26, 16, 5, PACIFIC);

    private NotifyFamily service() {
        return new NotifyFamily(new CommandExecutor(persister, eventStore), brevo,
                new FamilyNotificationMessages(new StaticAirportCityResolver(), ""),
                new ItineraryChangeNews(new StaticAirportCityResolver(), new HomeCities(List.of("San Francisco"))),
                true);
    }

    @Test
    void aFlightBookedOnItsOwnIsToldAsACancelledFlightThoughFamilyWereNeverToldItWasBooked() {
        given(brevo.configured()).willReturn(true);
        history(List.of(stored(fraSfo, BOOKING), cancelled(fraSfo)));

        NotifyFamily.Outcome outcome = service().notifyOfCancelledFlight(COMMAND_ID, fraSfo.flightId(), CANCELLING, NOW);

        assertThat(outcome)
                .as("no FamilyNotified in the history, and still sent")
                .isEqualTo(NotifyFamily.Outcome.SENT);
        assertThat(sentMessage().subject())
                .isEqualTo("(JitterTravel) Ted canceled a flight: FRA → SFO");
        verify(persister).saveCommand(COMMAND_ID,
                new NotifyFamilyCommand(NotifiedSubject.flight(fraSfo.flightId()), NotifiedFact.FLIGHT_CANCELLED, NOW));
    }

    @Test
    void aFlightEditedBeforeItWasCancelledIsDescribedAsLastWritten() {
        given(brevo.configured()).willReturn(true);
        FlightChanged later = new FlightChanged(fraSfo.flightId(), "United Airlines", "UA59",
                AirportCode.of("FRA"), at(11, 26, 15, 0, BERLIN), AirportCode.of("SFO"), at(11, 26, 17, 35, PACIFIC),
                "Schedule change");
        history(List.of(stored(fraSfo, BOOKING), stored(later, UUID.randomUUID()), cancelled(fraSfo)));

        service().notifyOfCancelledFlight(COMMAND_ID, fraSfo.flightId(), CANCELLING, NOW);

        assertThat(sentMessage().textContent())
                .contains("Was due to depart  Thu 26 Nov 2026, 3:00 PM (FRA)");
    }

    @Test
    void aLegOfAnItineraryWithFlightsStillToComeIsToldAsATripChange() {
        given(brevo.configured()).willReturn(true);
        List<StoredEvent> events = new ArrayList<>(booked());
        events.add(cancelled(berFra));
        history(events);

        NotifyFamily.Outcome outcome = service().notifyOfCancelledFlight(COMMAND_ID, berFra.flightId(), CANCELLING, NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.SENT);
        assertThat(sentMessage().textContent())
                .as("the journey home now starts in Frankfurt; the cancelled leg is among the old flights")
                .containsSubsequence(
                        "Berlin (BER) → San Francisco (SFO) is now Frankfurt (FRA) → San Francisco (SFO)",
                        "These are his flights now.", "1. UA59",
                        "These are the old flights that were changed:", "UA8786");
        verify(persister).saveCommand(COMMAND_ID,
                new NotifyFamilyCommand(NotifiedSubject.itinerary(HE80PP), NotifiedFact.ITINERARY_CHANGED, NOW));
    }

    @Test
    void aLegCancelledOnItsOwnIsToldEvenWhereAChangeWouldBeNoNews() {
        // A duplicate SFO->EWR an hour earlier than UA1642: cancelling it moves the outbound's start
        // by 1h15 and nothing else, which D5 would not send for.
        given(brevo.configured()).willReturn(true);
        FlightBooked sfoEwrEarlier = leg("UA1640", "SFO", 11, 7, 7, 0, PACIFIC, "EWR", 11, 7, 15, 20, NEW_YORK);
        List<StoredEvent> events = new ArrayList<>(List.of(
                stored(sfoEwrEarlier, BOOKING), stored(sfoEwr, BOOKING), stored(ewrBer, BOOKING),
                stored(new FlightItineraryBooked(HE80PP, "United Airlines", "HE80PP",
                        List.of(sfoEwrEarlier.flightId(), sfoEwr.flightId(), ewrBer.flightId())), BOOKING)));
        events.add(cancelled(sfoEwrEarlier));
        history(events);

        NotifyFamily.Outcome outcome = service().notifyOfCancelledFlight(COMMAND_ID, sfoEwrEarlier.flightId(),
                CANCELLING, Instant.parse("2026-11-01T00:00:00Z"));

        List<StoredEvent> before = events.subList(0, events.size() - 1).stream().toList();
        assertThat(new ItineraryChangeNews(new StaticAirportCityResolver(), new HomeCities(List.of()))
                .between(TripOnTheBooks.fold(before, "HE80PP").liveLegs(), TripOnTheBooks.fold(events, "HE80PP").liveLegs()))
                .as("precondition: D5 alone would not send for this")
                .isEmpty();
        assertThat(outcome)
                .as("the outbound still starts at SFO and ends at BER, moved under 2 hours: told anyway")
                .isEqualTo(NotifyFamily.Outcome.SENT);
    }

    @Test
    void theLastLegStillToComeIsToldAsACancelledTrip() {
        given(brevo.configured()).willReturn(true);
        List<StoredEvent> events = new ArrayList<>(booked());
        events.add(stored(new FlightCancelled(berFra.flightId(), "", NOW), UUID.randomUUID()));
        events.add(cancelled(fraSfo));
        history(events);

        NotifyFamily.Outcome outcome = service().notifyOfCancelledFlight(COMMAND_ID, fraSfo.flightId(), CANCELLING, NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.SENT);
        FamilyMessage message = sentMessage();
        assertThat(message.subject())
                .isEqualTo("(JitterTravel) Ted canceled a trip: FRA → SFO");
        assertThat(message.textContent())
                .as("only the leg this cancelled, not the outbound already flown")
                .contains("1. UA59")
                .doesNotContain("UA962");
        verify(persister).saveCommand(COMMAND_ID,
                new NotifyFamilyCommand(NotifiedSubject.itinerary(HE80PP), NotifiedFact.ITINERARY_CANCELLED, NOW));
    }

    @Test
    void aLegARebookingAddedBelongsToTheItinerary() {
        given(brevo.configured()).willReturn(true);
        FlightBooked fraSfoLater = leg("UA61", "FRA", 11, 27, 13, 30, BERLIN, "SFO", 11, 27, 16, 5, PACIFIC);
        List<StoredEvent> events = new ArrayList<>(booked());
        events.add(stored(fraSfoLater, UUID.randomUUID()));
        events.add(stored(new FlightItineraryRebooked(HE80PP, List.of(sfoEwr.flightId(), ewrBer.flightId(),
                berFra.flightId(), fraSfo.flightId(), fraSfoLater.flightId()), "Rebooked", NOW), UUID.randomUUID()));
        events.add(cancelled(fraSfoLater));
        history(events);

        service().notifyOfCancelledFlight(COMMAND_ID, fraSfoLater.flightId(), CANCELLING, NOW);

        verify(persister).saveCommand(COMMAND_ID,
                new NotifyFamilyCommand(NotifiedSubject.itinerary(HE80PP), NotifiedFact.ITINERARY_CHANGED, NOW));
    }

    @Test
    void aLegAScheduleChangeAddedBelongsToTheItinerary() {
        given(brevo.configured()).willReturn(true);
        FlightBooked fraSfoLater = leg("UA61", "FRA", 11, 27, 13, 30, BERLIN, "SFO", 11, 27, 16, 5, PACIFIC);
        List<StoredEvent> events = new ArrayList<>(booked());
        events.add(stored(fraSfoLater, UUID.randomUUID()));
        events.add(stored(new FlightItineraryChanged(HE80PP, List.of(sfoEwr.flightId(), ewrBer.flightId(),
                berFra.flightId(), fraSfo.flightId(), fraSfoLater.flightId()), "Airline schedule change", NOW),
                UUID.randomUUID()));
        events.add(cancelled(fraSfoLater));
        history(events);

        service().notifyOfCancelledFlight(COMMAND_ID, fraSfoLater.flightId(), CANCELLING, NOW);

        verify(persister).saveCommand(COMMAND_ID,
                new NotifyFamilyCommand(NotifiedSubject.itinerary(HE80PP), NotifiedFact.ITINERARY_CHANGED, NOW));
    }

    @Test
    void aFlightThatHadAlreadyDepartedIsCancelledInSilence() {
        given(brevo.configured()).willReturn(true);
        List<StoredEvent> events = new ArrayList<>(booked());
        events.add(cancelled(ewrBer));
        history(events);

        assertThat(service().notifyOfCancelledFlight(COMMAND_ID, ewrBer.flightId(), CANCELLING, NOW))
                .isEqualTo(NotifyFamily.Outcome.NO_LEGS);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void aFlightTheStreamNeverBookedHasNothingToDescribe() {
        given(brevo.configured()).willReturn(true);
        history(List.of());

        assertThat(service().notifyOfCancelledFlight(COMMAND_ID, FlightId.random(), CANCELLING, NOW))
                .isEqualTo(NotifyFamily.Outcome.NO_LEGS);
    }

    @Test
    void aDisabledNotifierSendsNothing() {
        NotifyFamily disabled = new NotifyFamily(new CommandExecutor(persister, eventStore), brevo,
                new FamilyNotificationMessages(new StaticAirportCityResolver(), ""), false);

        assertThat(disabled.notifyOfCancelledFlight(COMMAND_ID, fraSfo.flightId(), CANCELLING, NOW))
                .isEqualTo(NotifyFamily.Outcome.NOT_ENABLED);
        verify(brevo, never()).send(any());
    }

    private List<StoredEvent> booked() {
        return List.of(
                stored(sfoEwr, BOOKING), stored(ewrBer, BOOKING), stored(berFra, BOOKING), stored(fraSfo, BOOKING),
                stored(new FlightItineraryBooked(HE80PP, "United Airlines", "HE80PP",
                        List.of(sfoEwr.flightId(), ewrBer.flightId(), berFra.flightId(), fraSfo.flightId())), BOOKING));
    }

    private StoredEvent cancelled(FlightBooked leg) {
        return stored(new FlightCancelled(leg.flightId(), "Not going", NOW), CANCELLING);
    }

    private FamilyMessage sentMessage() {
        ArgumentCaptor<FamilyMessage> sent = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(brevo).send(sent.capture());
        return sent.getValue();
    }

    private void history(List<StoredEvent> events) {
        given(eventStore.findAll()).willAnswer(_ -> events.stream());
    }

    private static StoredEvent stored(Event event, UUID commandId) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), NOW, event, commandId);
    }

    private static FlightBooked leg(String number, String from, int departMonth, int departDay, int departHour,
                                    int departMinute, ZoneId departZone, String to, int arriveMonth, int arriveDay,
                                    int arriveHour, int arriveMinute, ZoneId arriveZone) {
        return new FlightBooked(FlightId.random(), "United Airlines", number,
                AirportCode.of(from), at(departMonth, departDay, departHour, departMinute, departZone),
                AirportCode.of(to), at(arriveMonth, arriveDay, arriveHour, arriveMinute, arriveZone));
    }

    private static ZonedTimestamp at(int month, int day, int hour, int minute, ZoneId zone) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, month, day, hour, minute), zone);
    }
}
