package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FamilyNotified;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancellationCause;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Family hear about a change to an itinerary when where or when a journey starts or ends moved
 * ({@code docs/FlightItineraryRebookingPlan.md} D5, D7, D8). The real executor over a mocked
 * persister and store, as in {@link NotifyFamilyTest}; the scenario is HE80PP's real rebooking.
 */
@ExtendWith(MockitoExtension.class)
class NotifyFamilyItineraryChangeTest {

    private static final Instant NOW = Instant.parse("2026-11-14T15:00:00Z");
    private static final UUID COMMAND_ID = UUID.randomUUID();
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID REBOOKING = UUID.randomUUID();
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

    private final List<StoredEvent> booked = List.of(
            stored(sfoEwr, BOOKING), stored(ewrBer, BOOKING), stored(berFra, BOOKING), stored(fraSfo, BOOKING),
            stored(new FlightItineraryBooked(HE80PP, "United Airlines", "HE80PP",
                    List.of(sfoEwr.flightId(), ewrBer.flightId(), berFra.flightId(), fraSfo.flightId())), BOOKING));

    private NotifyFamily service(boolean enabled) {
        return new NotifyFamily(new CommandExecutor(persister, eventStore), brevo,
                new FamilyNotificationMessages(new StaticAirportCityResolver(), ""),
                new ItineraryChangeNews(new StaticAirportCityResolver(), new HomeCities(List.of("San Francisco"))),
                enabled);
    }

    @Test
    void he80ppsReturnStartingInFrankfurtTellsFamilyTheFlightsStillToComeAndTheOneThatWent() {
        given(brevo.configured()).willReturn(true);
        history(rebookedWithoutBerFra());

        NotifyFamily.Outcome outcome = service(true).notifyOfChangedItinerary(COMMAND_ID, HE80PP, REBOOKING, NOW);

        assertThat(outcome)
                .as("told although family were never told it was booked (D8)")
                .isEqualTo(NotifyFamily.Outcome.SENT);
        ArgumentCaptor<FamilyMessage> sent = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(brevo).send(sent.capture());
        assertThat(sent.getValue().subject())
                .as("only the flights still to come")
                .isEqualTo("(JitterTravel) Ted's trip has changed: FRA → SFO");
        assertThat(sent.getValue().textContent())
                .contains("1. UA59")
                .contains("These are the old flights that were changed:")
                .contains("UA8786")
                .doesNotContain("UA1642");
        verify(persister).saveCommand(COMMAND_ID,
                new NotifyFamilyCommand(NotifiedSubject.itinerary(HE80PP), NotifiedFact.ITINERARY_CHANGED, NOW));
    }

    @Test
    void theFlightsStillToComeAndTheOldOnesAreEachInDepartureOrder() {
        given(brevo.configured()).willReturn(true);
        FlightChanged fraSfoLater = new FlightChanged(fraSfo.flightId(), "United Airlines", "UA59",
                AirportCode.of("FRA"), at(11, 26, 16, 30, BERLIN), AirportCode.of("SFO"), at(11, 26, 19, 5, PACIFIC),
                "Rebooked");
        List<StoredEvent> events = new ArrayList<>(rebookedWithoutBerFra());
        events.add(events.size() - 1, stored(fraSfoLater, REBOOKING));
        history(events);

        service(true).notifyOfChangedItinerary(COMMAND_ID, HE80PP, REBOOKING, Instant.parse("2026-11-01T00:00:00Z"));

        ArgumentCaptor<FamilyMessage> sent = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(brevo).send(sent.capture());
        String text = sent.getValue().textContent();
        assertThat(text)
                .as("three flights still to come, numbered in departure order")
                .containsSubsequence("1. UA1642", "2. UA962", "3. UA59",
                        "These are the old flights that were changed:", "UA8786", "UA59");
    }

    @Test
    void anotherItinerarysBookingDoesNotLendThisOneItsCode() {
        given(brevo.configured()).willReturn(true);
        FlightItineraryId other = FlightItineraryId.of(UUID.randomUUID());
        List<StoredEvent> events = new ArrayList<>();
        events.add(stored(new FlightItineraryBooked(other, "United Airlines", "MD7LKB", List.of()), UUID.randomUUID()));
        events.addAll(rebookedWithoutBerFra());
        history(events);

        assertThat(service(true).notifyOfChangedItinerary(COMMAND_ID, HE80PP, REBOOKING, NOW))
                .isEqualTo(NotifyFamily.Outcome.SENT);
    }

    @Test
    void aSecondChangeIsNewsOfItsOwnNotAlreadyTold() {
        given(brevo.configured()).willReturn(true);
        List<StoredEvent> events = new ArrayList<>(booked);
        events.add(stored(new FamilyNotified(NotifiedSubject.itinerary(HE80PP), NotifiedFact.ITINERARY_CHANGED,
                Instant.parse("2026-11-01T00:00:00Z")), UUID.randomUUID()));
        events.addAll(rebookedWithoutBerFra().subList(booked.size(), rebookedWithoutBerFra().size()));
        history(events);

        assertThat(service(true).notifyOfChangedItinerary(COMMAND_ID, HE80PP, REBOOKING, NOW))
                .isEqualTo(NotifyFamily.Outcome.SENT);
    }

    @Test
    void aChangeThatMovesOnlyAConnectionIsNoNewsAndWritesNothing() {
        given(brevo.configured()).willReturn(true);
        FlightChanged ewrBerLater = new FlightChanged(ewrBer.flightId(), "United Airlines", "UA962",
                AirportCode.of("EWR"), at(11, 7, 18, 20, NEW_YORK), AirportCode.of("BER"), at(11, 8, 8, 25, BERLIN),
                "Airline schedule change");
        List<StoredEvent> events = new ArrayList<>(booked);
        events.add(stored(ewrBerLater, REBOOKING));
        events.add(stored(new FlightItineraryRebooked(HE80PP,
                List.of(sfoEwr.flightId(), ewrBer.flightId(), berFra.flightId(), fraSfo.flightId()),
                "Rebooked", NOW), REBOOKING));
        history(events);

        NotifyFamily.Outcome outcome = service(true).notifyOfChangedItinerary(COMMAND_ID, HE80PP, REBOOKING, NOW);

        assertThat(outcome)
                .as("the outbound arrives 30 minutes later: no journey's end moved by more than 2 hours")
                .isEqualTo(NotifyFamily.Outcome.NOTHING_TO_SAY);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void aDisabledNotifierSendsNothing() {
        assertThat(service(false).notifyOfChangedItinerary(COMMAND_ID, HE80PP, REBOOKING, NOW))
                .isEqualTo(NotifyFamily.Outcome.NOT_ENABLED);
        verify(brevo, never()).send(any());
    }

    @Test
    void anItineraryTheStreamNeverBookedHasNothingToDescribe() {
        given(brevo.configured()).willReturn(true);
        history(List.of());

        assertThat(service(true).notifyOfChangedItinerary(COMMAND_ID, HE80PP, REBOOKING, NOW))
                .isEqualTo(NotifyFamily.Outcome.NO_LEGS);
    }

    @Test
    void aChangeWithEveryRemainingLegAlreadyFlownHasNothingToDescribe() {
        given(brevo.configured()).willReturn(true);
        history(rebookedWithoutBerFra());

        assertThat(service(true).notifyOfChangedItinerary(COMMAND_ID, HE80PP, REBOOKING,
                Instant.parse("2026-11-27T00:00:00Z")))
                .isEqualTo(NotifyFamily.Outcome.NO_LEGS);
        verify(brevo, never()).send(any());
    }

    /** HE80PP booked, then the rebooking's own append: BER->FRA dropped, the membership written. */
    private List<StoredEvent> rebookedWithoutBerFra() {
        List<StoredEvent> events = new ArrayList<>(booked);
        events.add(stored(new FlightCancelled(berFra.flightId(), "Rebooked", NOW, FlightCancellationCause.REBOOKED),
                REBOOKING));
        events.add(stored(new FlightItineraryRebooked(HE80PP,
                List.of(sfoEwr.flightId(), ewrBer.flightId(), berFra.flightId(), fraSfo.flightId()),
                "Rebooked", NOW), REBOOKING));
        return events;
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
