package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.FamilyNotified;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryRebooked;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.NotifiedSubject;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.FamilyNotificationMessages;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Tells family something, once. The decision is folded from the event stream (R1): the most recent
 * {@link FamilyNotified} for the subject says what family currently believe, and a notification
 * that would only repeat it is no news.
 * <p>
 * <strong>Three things happen before a command exists, and all three write nothing.</strong> A
 * notifier that is switched off or has no key or recipient must not litter {@code command_log} with
 * rows for mail it never meant to send; and a fact family already have is "nothing to say", not a
 * failure, which would otherwise flood the failed-command surface with noise. The last is safe
 * outside the command because reactors run on one thread, so there is no concurrent second trigger
 * for one subject.
 * <p>
 * Then the send itself goes through {@link CommandExecutor#executeExternalAction}: the intent is
 * written first, the email is the work, and {@link FamilyNotified} is appended only once Brevo
 * accepted it. A failed send leaves a FAILED_SEND row and no event. Never takes an
 * {@code EventStore}, and the switch arrives as a plain {@code boolean} so nothing here imports
 * Spring.
 * <p>
 * The id and the time come from the caller: the boundary captures them and passes them inward.
 */
public class NotifyFamily {

    /** What happened to a request, so the caller can count it without this class knowing about metrics. */
    public enum Outcome {
        SENT, NOT_ENABLED, NOT_CONFIGURED, ALREADY_TOLD,
        /** A trip was cancelled that family were never told was booked: there is nothing to retract. */
        NEVER_TOLD,
        /** A cancellation named flights the stream does not know, so there is nothing to describe. */
        NO_LEGS,
        /** A conference that is only being watched, or that the stream does not know: never news. */
        NOTHING_TO_SAY
    }

    private final CommandExecutor commandExecutor;
    private final BrevoEmailClient brevo;
    private final FamilyNotificationMessages messages;
    private final ItineraryChangeNews changeNews;
    private final boolean enabled;

    /** With no home cities configured, for the callers that never tell family about a change. */
    public NotifyFamily(CommandExecutor commandExecutor, BrevoEmailClient brevo,
                        FamilyNotificationMessages messages, boolean enabled) {
        this(commandExecutor, brevo, messages,
                new ItineraryChangeNews(new StaticAirportCityResolver(), new HomeCities(List.of())), enabled);
    }

    public NotifyFamily(CommandExecutor commandExecutor, BrevoEmailClient brevo,
                        FamilyNotificationMessages messages, ItineraryChangeNews changeNews, boolean enabled) {
        this.commandExecutor = commandExecutor;
        this.brevo = brevo;
        this.messages = messages;
        this.changeNews = changeNews;
        this.enabled = enabled;
    }

    public Outcome notifyFamily(UUID commandId, NotifiedSubject subject, NotifiedFact fact,
                                List<FlightBooked> legs, Instant now) {
        Optional<Outcome> blocked = blocked();
        if (blocked.isPresent()) {
            return blocked.get();
        }
        if (lastToldAbout(history(), subject).filter(fact::equals).isPresent()) {
            return Outcome.ALREADY_TOLD;
        }
        send(commandId, subject, fact, messages.messageFor(fact, legs), now);
        return Outcome.SENT;
    }

    /**
     * A whole itinerary was cancelled, and {@code cancelledFlights} are the legs it cancelled with it.
     * <p>
     * <strong>Told only if family were told it was booked</strong> (Ted, 2026-10-05): "a trip you
     * were told about is off" is news, and "a trip you never heard of is off" is not. That is the same
     * positive-first rule a conference exit follows, and it is its own named condition rather than
     * part of the comparison, because it is a different rule with a different reason. A trip booked
     * before the notifier existed, or while it was switched off, is therefore cancelled in silence.
     * <p>
     * The legs are described as they stood when cancelled (a later hand edit applies), and only the
     * ones this cancellation cancelled: one cancelled earlier on its own is not part of what family
     * are being told is off.
     */
    public Outcome notifyOfCancelledItinerary(UUID commandId, FlightItineraryId itinerary,
                                              Set<FlightId> cancelledFlights, Instant now) {
        Optional<Outcome> blocked = blocked();
        if (blocked.isPresent()) {
            return blocked.get();
        }
        NotifiedSubject subject = NotifiedSubject.itinerary(itinerary);
        List<StoredEvent> history = history();
        Optional<NotifiedFact> last = lastToldAbout(history, subject);
        if (last.isEmpty()) {
            return Outcome.NEVER_TOLD;
        }
        if (last.get() == NotifiedFact.ITINERARY_CANCELLED) {
            return Outcome.ALREADY_TOLD;
        }
        List<FlightBooked> legs = legsAsTheyStood(history, cancelledFlights);
        if (legs.isEmpty()) {
            return Outcome.NO_LEGS;
        }
        send(commandId, subject, NotifiedFact.ITINERARY_CANCELLED,
                messages.messageFor(NotifiedFact.ITINERARY_CANCELLED, legs), now);
        return Outcome.SENT;
    }

    /**
     * An itinerary was changed, by the airline or by Ted, in the append whose command id is
     * {@code changeCommand} ({@code docs/FlightItineraryRebookingPlan.md} D5, D7, D8).
     * <p>
     * The batch carries only new values, so the itinerary is folded twice: as it stood before that
     * append, and as it stands now. {@link ItineraryChangeNews} decides from the two whether where or
     * when a journey starts or ends changed; a change that moved only a connection is no news.
     * <strong>Told whether or not family were told it was booked</strong> (D8), which is why there is
     * no positive-first check here: the email lists every flight still to come, so it stands on its
     * own. And never "already told": each change is news of its own.
     */
    public Outcome notifyOfChangedItinerary(UUID commandId, FlightItineraryId itinerary, UUID changeCommand,
                                            Instant now) {
        Optional<Outcome> blocked = blocked();
        if (blocked.isPresent()) {
            return blocked.get();
        }
        List<StoredEvent> history = history();
        Optional<String> code = confirmationCodeOf(history, itinerary);
        if (code.isEmpty()) {
            return Outcome.NO_LEGS;
        }
        BeforeAndAfter change = foldedAroundTheAppend(history, code.get(), changeCommand);
        if (changeNews.between(change.before(), change.after()).isEmpty()) {
            return Outcome.NOTHING_TO_SAY;
        }
        List<FlightBooked> stillToCome = stillToCome(change.after(), now);
        if (stillToCome.isEmpty()) {
            return Outcome.NO_LEGS;
        }
        sendTripChanged(commandId, itinerary, change, stillToCome, now);
        return Outcome.SENT;
    }

    /**
     * Ted cancelled one flight on its own (Cancel on its row), in the append whose command id is
     * {@code cancelCommand}. <strong>Always told</strong> (Ted, 2026-11-16): whether or not family
     * were told it was booked, and with no D5 test, because Ted cancelling a flight is news.
     * <ul>
     *   <li>A flight booked on its own: "Ted canceled a flight".</li>
     *   <li>A leg of an itinerary with flights still to come: the same email a change sends, the
     *       cancelled leg among the old flights.</li>
     *   <li>The last leg of an itinerary still to come: "Ted canceled a trip", naming that leg, since
     *       a change email would have no flights to list.</li>
     * </ul>
     * A flight that had already departed is history, not news, and is cancelled in silence.
     * <p>
     * Until 2026-11-16 a lone cancellation was silent, because one dropped leg was often a
     * rebooking done by hand (Ted, 2026-10-05). A rebooking now has its own event, so the actuator
     * calls this only for a cancellation that arrives without one.
     */
    public Outcome notifyOfCancelledFlight(UUID commandId, FlightId flight, UUID cancelCommand, Instant now) {
        Optional<Outcome> blocked = blocked();
        if (blocked.isPresent()) {
            return blocked.get();
        }
        List<StoredEvent> history = history();
        List<FlightBooked> cancelled = legsAsTheyStood(history, Set.of(flight));
        if (cancelled.isEmpty() || !cancelled.getFirst().departureDateTime().utc().isAfter(now)) {
            return Outcome.NO_LEGS;
        }
        Optional<FlightItineraryId> itinerary = itineraryOf(history, flight);
        Optional<String> code = itinerary.flatMap(id -> confirmationCodeOf(history, id));
        if (itinerary.isEmpty() || code.isEmpty()) {
            send(commandId, NotifiedSubject.flight(flight), NotifiedFact.FLIGHT_CANCELLED,
                    messages.messageFor(NotifiedFact.FLIGHT_CANCELLED, cancelled), now);
            return Outcome.SENT;
        }
        BeforeAndAfter change = foldedAroundTheAppend(history, code.get(), cancelCommand);
        List<FlightBooked> stillToCome = stillToCome(change.after(), now);
        if (stillToCome.isEmpty()) {
            send(commandId, NotifiedSubject.itinerary(itinerary.get()), NotifiedFact.ITINERARY_CANCELLED,
                    messages.messageFor(NotifiedFact.ITINERARY_CANCELLED, cancelled), now);
            return Outcome.SENT;
        }
        sendTripChanged(commandId, itinerary.get(), change, stillToCome, now);
        return Outcome.SENT;
    }

    /** The itinerary's live legs as they stood before the append {@code command} wrote, and after. */
    private record BeforeAndAfter(List<ItineraryLeg> before, List<ItineraryLeg> after) {
    }

    private BeforeAndAfter foldedAroundTheAppend(List<StoredEvent> history, String confirmationCode, UUID command) {
        List<StoredEvent> beforeTheAppend = history.stream()
                .filter(stored -> !stored.commandId().equals(command))
                .toList();
        return new BeforeAndAfter(TripOnTheBooks.fold(beforeTheAppend, confirmationCode).liveLegs(),
                TripOnTheBooks.fold(history, confirmationCode).liveLegs());
    }

    private List<FlightBooked> stillToCome(List<ItineraryLeg> legs, Instant now) {
        return legs.stream()
                .filter(leg -> leg.departureDateTime().utc().isAfter(now))
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .map(ItineraryLeg::booked)
                .toList();
    }

    private void sendTripChanged(UUID commandId, FlightItineraryId itinerary, BeforeAndAfter change,
                                 List<FlightBooked> stillToCome, Instant now) {
        send(commandId, NotifiedSubject.itinerary(itinerary), NotifiedFact.ITINERARY_CHANGED,
                messages.tripChanged(stillToCome, changeNews.changedJourneys(change.before(), change.after()),
                        oldFlights(change.before(), change.after())), now);
    }

    /**
     * The itinerary {@code flight} last belonged to. An explicit loop: membership is rewritten by
     * a schedule change or a rebooking, so the last event that names the flight decides.
     */
    private Optional<FlightItineraryId> itineraryOf(List<StoredEvent> history, FlightId flight) {
        FlightItineraryId itinerary = null;
        for (StoredEvent stored : history) {
            switch (stored.payload()) {
                case FlightItineraryBooked e when e.flightIds().contains(flight) -> itinerary = e.itineraryId();
                case FlightItineraryChanged e when e.flightIds().contains(flight) -> itinerary = e.itineraryId();
                case FlightItineraryRebooked e when e.flightIds().contains(flight) -> itinerary = e.itineraryId();
                default -> { /* does not name this flight */ }
            }
        }
        return Optional.ofNullable(itinerary);
    }

    /** Every leg the change dropped, and every one it moved at its old times, in departure order. */
    private List<FlightBooked> oldFlights(List<ItineraryLeg> before, List<ItineraryLeg> after) {
        return before.stream()
                .filter(was -> after.stream().noneMatch(was::equals))
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .map(ItineraryLeg::booked)
                .toList();
    }

    private Optional<String> confirmationCodeOf(List<StoredEvent> history, FlightItineraryId itinerary) {
        return history.stream()
                .map(StoredEvent::payload)
                .filter(FlightItineraryBooked.class::isInstance)
                .map(FlightItineraryBooked.class::cast)
                .filter(booked -> booked.itineraryId().equals(itinerary))
                .map(FlightItineraryBooked::confirmationCode)
                .findFirst();
    }

    /**
     * Something happened to a conference's commitment, and the question is only whether what family
     * should now believe differs from what they were last told. The conference is folded from the
     * stream ({@link ConferenceStanding}), so the five triggering events are interchangeable here:
     * a talk accepted after a confirmation folds to the same "going" and is {@code ALREADY_TOLD}.
     * <p>
     * Merely watching a conference is never news, and an exit is <strong>told only where "going"
     * went first</strong> ({@link #familyWereToldHeWasGoing}): a conference family never heard of
     * is dropped in silence, which is what keeps a rejection from announcing a conference nobody
     * knew he was considering.
     */
    public Outcome notifyOfConference(UUID commandId, ConferenceId conference, Instant now) {
        Optional<Outcome> blocked = blocked();
        if (blocked.isPresent()) {
            return blocked.get();
        }
        List<StoredEvent> history = history();
        Optional<ConferenceStanding> standing = ConferenceStanding.foldOf(history, conference);
        Optional<NotifiedFact> fact = standing.flatMap(ConferenceStanding::fact);
        if (fact.isEmpty()) {
            return Outcome.NOTHING_TO_SAY;
        }
        NotifiedSubject subject = NotifiedSubject.conference(conference);
        Optional<NotifiedFact> last = lastToldAbout(history, subject);
        if (last.filter(fact.get()::equals).isPresent()) {
            return Outcome.ALREADY_TOLD;
        }
        if (fact.get() == NotifiedFact.CONFERENCE_NOT_GOING && !familyWereToldHeWasGoing(last)) {
            return Outcome.NEVER_TOLD;
        }
        ConferenceStanding told = standing.orElseThrow();
        FamilyMessage message = fact.get() == NotifiedFact.CONFERENCE_GOING
                ? messages.conferenceGoing(told.news())
                : messages.conferenceNotGoing(told.news(), told.endedBy());
        send(commandId, subject, fact.get(), message, now);
        return Outcome.SENT;
    }

    /** The positive-first rule, named: an exit is a correction, so it needs something to correct. */
    private boolean familyWereToldHeWasGoing(Optional<NotifiedFact> last) {
        return last.filter(NotifiedFact.CONFERENCE_GOING::equals).isPresent();
    }

    /** The switch and the means to send, checked before any command row exists. */
    private Optional<Outcome> blocked() {
        if (!enabled) {
            return Optional.of(Outcome.NOT_ENABLED);
        }
        if (!brevo.configured()) {
            return Optional.of(Outcome.NOT_CONFIGURED);
        }
        return Optional.empty();
    }

    private void send(UUID commandId, NotifiedSubject subject, NotifiedFact fact, FamilyMessage message,
                      Instant now) {
        commandExecutor.executeExternalAction(commandId, new NotifyFamilyCommand(subject, fact, now), () -> {
            brevo.send(message);
            return Stream.of(new FamilyNotified(subject, fact, now));
        });
    }

    private List<StoredEvent> history() {
        return commandExecutor.eventsForDecision().toList();
    }

    /** An explicit loop: what family believe now is the <em>last</em> thing they were told. */
    private Optional<NotifiedFact> lastToldAbout(List<StoredEvent> history, NotifiedSubject subject) {
        NotifiedFact last = null;
        for (StoredEvent stored : history) {
            if (stored.payload() instanceof FamilyNotified told && told.subject().equals(subject)) {
                last = told.fact();
            }
        }
        return Optional.ofNullable(last);
    }

    /**
     * Each requested flight as the stream last wrote it, in departure order. An explicit loop: a
     * later {@code FlightChanged} must replace an earlier booking, so the order of events decides.
     */
    private List<FlightBooked> legsAsTheyStood(List<StoredEvent> history, Set<FlightId> wanted) {
        Map<FlightId, FlightBooked> legs = new LinkedHashMap<>();
        for (StoredEvent stored : history) {
            switch (stored.payload()) {
                case FlightBooked booked when wanted.contains(booked.flightId()) ->
                        legs.put(booked.flightId(), booked);
                case FlightChanged changed when wanted.contains(changed.flightId()) ->
                        legs.put(changed.flightId(), new FlightBooked(changed.flightId(), changed.airline(),
                                changed.flightNumber(), changed.departureAirport(), changed.departureDateTime(),
                                changed.arrivalAirport(), changed.arrivalDateTime()));
                default -> { /* not one of the cancelled legs */ }
            }
        }
        return legs.values().stream()
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .toList();
    }
}
