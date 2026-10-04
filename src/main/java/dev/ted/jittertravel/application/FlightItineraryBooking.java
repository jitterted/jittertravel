package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookFlightContext;
import dev.ted.jittertravel.domain.BookFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryContext;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryRefused;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * The decision context is folded from the event stream (R1), shared with the four single-leg write
 * paths through {@link LiveScheduledLegs}. Ids and {@code now} arrive from the boundary.
 * <p>
 * <strong>The pasted text never reaches the command log.</strong> The command carries parsed legs
 * only; the request passed to {@link CommandExecutor} is used solely for a read-only refusal's
 * message, and the request type's {@code toString} leaves the paste out.
 */
public class FlightItineraryBooking {

    private final CommandExecutor commandExecutor;
    private final AirportZoneResolver airportZoneResolver;
    private final LiveScheduledLegs liveScheduledLegs;
    private final UnitedItineraryParser parser = new UnitedItineraryParser();

    public FlightItineraryBooking(CommandExecutor commandExecutor, AirportZoneResolver airportZoneResolver,
                                  LiveScheduledLegs liveScheduledLegs) {
        this.commandExecutor = commandExecutor;
        this.airportZoneResolver = airportZoneResolver;
        this.liveScheduledLegs = liveScheduledLegs;
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
        } else if (evaluation.changeable()) {
            ItineraryEvaluation.ScheduleChange change = evaluation.scheduleChange();
            commandExecutor.execute(itineraryId.id(), request, change.context(), change.command());
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
        return switch (parser.parse(pasted)) {
            case UnitedItineraryParser.Unparseable unparseable -> ItineraryEvaluation.unparseable(unparseable.problems());
            case UnitedItineraryParser.Parsed parsed ->
                    evaluateParsed(parsed.itinerary(), airportZones, itineraryId, newFlightId, context);
        };
    }

    private ItineraryEvaluation evaluateParsed(PastedItinerary itinerary, Map<String, String> airportZones,
                                               FlightItineraryId itineraryId, Supplier<FlightId> newFlightId,
                                               BookFlightContext context) {
        Map<AirportCode, ZoneId> zones = new LinkedHashMap<>();
        Set<AirportCode> unresolved = new LinkedHashSet<>();
        FlightEndpointZone endpointZone = new FlightEndpointZone(airportZoneResolver);
        for (PastedItinerary.Leg leg : itinerary.legs()) {
            for (AirportCode airport : List.of(leg.departureAirport(), leg.arrivalAirport())) {
                try {
                    zones.putIfAbsent(airport, endpointZone.resolve(airportZones.get(airport.code()), airport));
                } catch (ZoneResolutionException unknownAirport) {
                    unresolved.add(airport);
                }
            }
        }
        List<ItineraryEvaluation.Leg> unjudged = itinerary.legs().stream()
                .map(leg -> new ItineraryEvaluation.Leg(leg, null))
                .toList();
        if (!unresolved.isEmpty()) {
            // Nothing can be judged until every time has a zone: an instant is what the rules compare.
            return new ItineraryEvaluation(List.of(), itinerary.confirmationCode(), unjudged,
                    List.copyOf(unresolved), null);
        }

        List<ItineraryLeg> pastedLegs = itinerary.legs().stream()
                .map(leg -> resolved(leg, zones, newFlightId))
                .toList();
        TripOnTheBooks booked = TripOnTheBooks.fold(commandExecutor.eventsForDecision().toList(),
                itinerary.confirmationCode());
        if (booked.cancelledOnly()) {
            return ItineraryEvaluation.unparseable(List.of("Itinerary " + itinerary.confirmationCode()
                                                           + " was cancelled; it cannot be changed"));
        }
        if (booked.live()) {
            ChangeFlightItineraryCommand change = new ChangeFlightItineraryCommand(booked.itineraryId(), pastedLegs);
            ChangeFlightItineraryContext changeContext = booked.contextFor(context);
            return ItineraryEvaluation.scheduleChange(itinerary.confirmationCode(),
                    new ItineraryEvaluation.ScheduleChange(change, changeContext, change.plan(changeContext)));
        }

        String airline = itinerary.legs().getFirst().airline();
        BookFlightItineraryCommand command = new BookFlightItineraryCommand(itineraryId, airline,
                itinerary.confirmationCode(),
                pastedLegs);
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

    private static ItineraryLeg resolved(PastedItinerary.Leg leg, Map<AirportCode, ZoneId> zones,
                                         Supplier<FlightId> newFlightId) {
        return new ItineraryLeg(newFlightId.get(), leg.airline(), leg.flightNumber(),
                leg.departureAirport(),
                ZonedTimestamp.fromLocal(leg.departureLocal(), zones.get(leg.departureAirport())),
                leg.arrivalAirport(),
                ZonedTimestamp.fromLocal(leg.arrivalLocal(), zones.get(leg.arrivalAirport())));
    }
}
