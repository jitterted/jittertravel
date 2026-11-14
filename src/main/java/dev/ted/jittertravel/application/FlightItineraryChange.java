package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookFlightContext;
import dev.ted.jittertravel.domain.ChangeFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryContext;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import dev.ted.jittertravel.domain.ItineraryChangePlan;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Records a change to one booked itinerary from the email sent after it: the airline's schedule
 * change or Ted's own rebooking, as he says on the page ({@code docs/FlightItineraryRebookingPlan.md}
 * D1–D3). It starts from the itinerary, named by its id, never from the code in the paste: that is
 * the whole of D11, and a paste for a different code is refused rather than applied to that one.
 * <p>
 * <strong>Preview and applying are one evaluation</strong>, as for booking: {@link #evaluate} runs the
 * real {@link ItineraryChangePlan} against the folded itinerary (R1) and writes nothing, and
 * {@link #apply} runs the same evaluation and hands its command to {@link CommandExecutor} only when
 * it is clean. Ids and {@code now} arrive from the boundary. The pasted text never reaches the
 * command log: the command carries parsed legs only.
 */
public class FlightItineraryChange {

    private final CommandExecutor commandExecutor;
    private final LiveScheduledLegs liveScheduledLegs;
    private final ItineraryPasteReader pasteReader;

    public FlightItineraryChange(CommandExecutor commandExecutor, AirportZoneResolver airportZoneResolver,
                                 LiveScheduledLegs liveScheduledLegs) {
        this.commandExecutor = commandExecutor;
        this.liveScheduledLegs = liveScheduledLegs;
        this.pasteReader = new ItineraryPasteReader(airportZoneResolver);
    }

    /**
     * What applying this paste to the itinerary would record, writing nothing.
     *
     * @param kind who made the change, or null while Ted has not said
     */
    public ItineraryChangeEvaluation evaluate(FlightItineraryId itineraryId, ItineraryChangeKind kind,
                                              String pasted, Map<String, String> airportZones,
                                              Supplier<FlightId> newFlightId, Instant now) {
        List<StoredEvent> events = commandExecutor.eventsForDecision().toList();
        String code = confirmationCodeOf(itineraryId, events);
        return switch (pasteReader.read(pasted, airportZones, newFlightId)) {
            case ItineraryPasteReader.Unreadable unreadable ->
                    ItineraryChangeEvaluation.unread(unreadable.problems(), List.of(), kind);
            case ItineraryPasteReader.ZonesNeeded needed ->
                    ItineraryChangeEvaluation.unread(wrongEmail(needed.itinerary(), code), needed.unresolved(), kind);
            case ItineraryPasteReader.Read read -> {
                List<String> wrong = wrongEmail(read.itinerary(), code);
                if (!wrong.isEmpty()) {
                    yield ItineraryChangeEvaluation.unread(wrong, List.of(), kind);
                }
                TripOnTheBooks trip = TripOnTheBooks.fold(events, code);
                if (!trip.live() || !trip.itineraryId().equals(itineraryId)) {
                    yield ItineraryChangeEvaluation.unread(
                            List.of("Itinerary " + code + " was cancelled; it cannot be changed"), List.of(), kind);
                }
                ChangeFlightItineraryContext context =
                        trip.contextFor(new BookFlightContext(now, liveScheduledLegs.fold()));
                ChangeFlightItineraryCommand command = kind == null
                        ? null
                        : new ChangeFlightItineraryCommand(itineraryId, kind, read.legs());
                yield new ItineraryChangeEvaluation(List.of(), List.of(), kind,
                        ItineraryChangePlan.of(read.legs(), context), context, command);
            }
        };
    }

    /**
     * Records the change if it evaluates clean and says who made it, and returns the evaluation
     * either way; the caller re-renders it when {@link ItineraryChangeEvaluation#applicable()} is
     * false. A second submit of the same form finds nothing left to change, and records nothing.
     */
    public ItineraryChangeEvaluation apply(UUID commandId, Object request, FlightItineraryId itineraryId,
                                           ItineraryChangeKind kind, String pasted,
                                           Map<String, String> airportZones, Supplier<FlightId> newFlightId,
                                           Instant now) {
        ItineraryChangeEvaluation evaluation = evaluate(itineraryId, kind, pasted, airportZones, newFlightId, now);
        if (evaluation.applicable()) {
            commandExecutor.execute(commandId, request, evaluation.context(), evaluation.command());
        }
        return evaluation;
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }

    /** One sentence under the paste box, naming both codes, so a mix-up between two emails is plain. */
    private List<String> wrongEmail(PastedItinerary pasted, String code) {
        return pasted.confirmationCode().equals(code)
                ? List.of()
                : List.of("This email is for " + pasted.confirmationCode() + ", not " + code);
    }

    private String confirmationCodeOf(FlightItineraryId itineraryId, List<StoredEvent> events) {
        return events.stream()
                .map(StoredEvent::payload)
                .filter(FlightItineraryBooked.class::isInstance)
                .map(FlightItineraryBooked.class::cast)
                .filter(booked -> booked.itineraryId().equals(itineraryId))
                .map(FlightItineraryBooked::confirmationCode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No itinerary " + itineraryId.id() + " was booked"));
    }
}
