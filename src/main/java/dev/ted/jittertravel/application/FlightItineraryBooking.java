package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookFlightContext;
import dev.ted.jittertravel.domain.BookFlightItineraryCommand;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryRefused;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Books a whole itinerary from a pasted confirmation email.
 * <p>
 * <strong>Preview and booking are one evaluation.</strong> {@link #evaluate} parses, settles each
 * airport's zone, and then runs the real {@link BookFlightItineraryCommand} against the live
 * schedule without writing — so the preview shows exactly the refusals booking would meet, from the
 * command's own rules rather than a second copy of them. {@link #book} runs the same evaluation and,
 * only if it is clean, hands that same command to {@link CommandExecutor}.
 * <p>
 * <strong>It only ever books.</strong> A paste whose code is already a live itinerary is refused with
 * {@link ItineraryEvaluation#onTheBooks()}, and the change is recorded on that itinerary's own page
 * by {@link FlightItineraryChange} ({@code docs/FlightItineraryRebookingPlan.md} D11).
 * <p>
 * The decision context is folded from the event stream (R1), shared with the four single-leg write
 * paths through {@link LiveScheduledLegs}. Ids and {@code now} arrive from the boundary.
 * <p>
 * <strong>The pasted text never reaches the command log.</strong> The command carries parsed legs
 * only; the request passed to {@link CommandExecutor} is used solely for a read-only refusal's
 * message, and the request type's {@code toString} leaves the paste out.
 */
public class FlightItineraryBooking {

    private final CommandExecutor commandExecutor;
    private final LiveScheduledLegs liveScheduledLegs;
    private final ItineraryPasteReader pasteReader;

    public FlightItineraryBooking(CommandExecutor commandExecutor, AirportZoneResolver airportZoneResolver,
                                  LiveScheduledLegs liveScheduledLegs) {
        this.commandExecutor = commandExecutor;
        this.liveScheduledLegs = liveScheduledLegs;
        this.pasteReader = new ItineraryPasteReader(airportZoneResolver);
    }

    /**
     * What booking this paste would do, writing nothing.
     *
     * @param airportZones zones Ted picked for airports the curated table does not know, keyed by
     *                     airport code; one pick covers every leg through that airport
     */
    public ItineraryEvaluation evaluate(String pasted, Map<String, String> airportZones,
                                        FlightItineraryId itineraryId, Supplier<FlightId> newFlightId,
                                        Instant now) {
        return evaluate(pasted, airportZones, itineraryId, newFlightId, context(now));
    }

    /**
     * Books the paste if it evaluates clean, and returns the evaluation either way — the caller
     * re-renders it when {@link ItineraryEvaluation#bookable()} is false.
     */
    public ItineraryEvaluation book(Object request, String pasted, Map<String, String> airportZones,
                                    FlightItineraryId itineraryId, Supplier<FlightId> newFlightId,
                                    Instant now) {
        if (alreadyBooked(itineraryId)) {
            return ItineraryEvaluation.theFormWasAlreadyBooked();
        }
        BookFlightContext context = context(now);
        ItineraryEvaluation evaluation = evaluate(pasted, airportZones, itineraryId, newFlightId, context);
        if (evaluation.bookable()) {
            commandExecutor.execute(itineraryId.id(), request, context, evaluation.command());
        }
        return evaluation;
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }

    /**
     * Folded from the event stream (R1). The itinerary id is also the command id, so booking it a
     * second time — a double click, a stale tab after the flights were cancelled — would meet the
     * write-ahead log's primary key as an error page. Answering "already booked" is what the reader
     * wanted to know, and it also stops a resubmit being worded as a pile of overlaps with itself.
     */
    private boolean alreadyBooked(FlightItineraryId itineraryId) {
        return commandExecutor.eventsForDecision()
                .anyMatch(stored -> stored.payload() instanceof FlightItineraryBooked booked
                                    && booked.itineraryId().equals(itineraryId));
    }

    private BookFlightContext context(Instant now) {
        return new BookFlightContext(now, liveScheduledLegs.fold());
    }

    private ItineraryEvaluation evaluate(String pasted, Map<String, String> airportZones,
                                         FlightItineraryId itineraryId, Supplier<FlightId> newFlightId,
                                         BookFlightContext context) {
        return switch (pasteReader.read(pasted, airportZones, newFlightId)) {
            case ItineraryPasteReader.Unreadable unreadable -> ItineraryEvaluation.unparseable(unreadable.problems());
            // A code already booked is refused before any zone is asked for: no pick could help.
            case ItineraryPasteReader.ZonesNeeded needed -> alreadyKnown(needed.itinerary())
                    .orElseGet(() -> new ItineraryEvaluation(List.of(), needed.itinerary().confirmationCode(),
                            unjudged(needed.itinerary()), needed.unresolved(), null));
            case ItineraryPasteReader.Read read -> alreadyKnown(read.itinerary())
                    .orElseGet(() -> evaluateRead(read, itineraryId, context));
        };
    }

    /** Refused if the code names an itinerary already on the books, live or cancelled (R1). */
    private Optional<ItineraryEvaluation> alreadyKnown(PastedItinerary itinerary) {
        TripOnTheBooks booked = TripOnTheBooks.fold(commandExecutor.eventsForDecision().toList(),
                itinerary.confirmationCode());
        if (booked.cancelledOnly()) {
            return Optional.of(ItineraryEvaluation.unparseable(List.of("Itinerary " + itinerary.confirmationCode()
                                                                       + " was canceled; it cannot be changed")));
        }
        if (booked.live()) {
            return Optional.of(ItineraryEvaluation.alreadyOnTheBooks(itinerary.confirmationCode(), booked.onTheBooks()));
        }
        return Optional.empty();
    }

    private ItineraryEvaluation evaluateRead(ItineraryPasteReader.Read read, FlightItineraryId itineraryId,
                                             BookFlightContext context) {
        PastedItinerary itinerary = read.itinerary();
        List<ItineraryEvaluation.Leg> unjudged = unjudged(itinerary);
        BookFlightItineraryCommand command = new BookFlightItineraryCommand(itineraryId,
                itinerary.legs().getFirst().airline(), itinerary.confirmationCode(), read.legs());
        try {
            // A dry run of the real rules: execute() is pure, and writes nothing until the executor.
            command.execute(context).toList();
            return new ItineraryEvaluation(List.of(), itinerary.confirmationCode(), unjudged, List.of(), command);
        } catch (FlightItineraryRefused refused) {
            List<ItineraryEvaluation.Leg> judged = new ArrayList<>(unjudged);
            refused.refusals().forEach(refusal -> judged.set(refusal.legNumber() - 1,
                    new ItineraryEvaluation.Leg(judged.get(refusal.legNumber() - 1).pasted(), refusal.reason())));
            return new ItineraryEvaluation(List.of(), itinerary.confirmationCode(), judged, List.of(), null);
        }
    }

    private List<ItineraryEvaluation.Leg> unjudged(PastedItinerary itinerary) {
        return itinerary.legs().stream()
                .map(leg -> new ItineraryEvaluation.Leg(leg, null))
                .toList();
    }
}
