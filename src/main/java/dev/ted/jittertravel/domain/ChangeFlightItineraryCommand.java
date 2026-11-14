package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.domain.FlightItineraryRefused.LegRefusal;
import dev.ted.jittertravel.domain.ItineraryChangePlan.LegDiff;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Applies a change to an itinerary, either the airline's schedule change or Ted's own rebooking
 * ({@link ItineraryChangeKind}, picked on the change page because the email cannot tell). The
 * pasted legs are what the itinerary now holds, and the booked legs are made to agree. One command,
 * several events, in one append — a half-applied reroute (the new leg booked, the old one not
 * cancelled) is exactly the overlapping state the entry-time check exists to refuse.
 * <p>
 * The rules live in {@link ItineraryChangePlan}, and are the same for both kinds. What the kind
 * decides is the fact recorded ({@link FlightItineraryChanged} or {@link FlightItineraryRebooked}),
 * the reason on each leg's event, and the cause on a dropped leg
 * ({@code docs/FlightItineraryRebookingPlan.md} D1, D2).
 * <p>
 * A paste that matches what is booked leg for leg is refused with {@link FlightItineraryUnchanged}
 * rather than recorded.
 */
public record ChangeFlightItineraryCommand(
        FlightItineraryId itineraryId,
        ItineraryChangeKind kind,
        List<ItineraryLeg> pastedLegs
) implements DomainCommand<ChangeFlightItineraryContext> {

    static final String SCHEDULE_CHANGE_REASON = "Airline schedule change";
    static final String REBOOKING_REASON = "Rebooked";

    public ChangeFlightItineraryCommand {
        if (kind == null) {
            throw new IllegalArgumentException("A change says who made it");
        }
        if (pastedLegs == null || pastedLegs.isEmpty()) {
            throw new IllegalArgumentException("A schedule change has at least one leg");
        }
        pastedLegs = List.copyOf(pastedLegs);
    }

    /** The diff and every refusal against it, writing nothing; what the preview shows. */
    public ItineraryChangePlan plan(ChangeFlightItineraryContext context) {
        return ItineraryChangePlan.of(pastedLegs, context);
    }

    @Override
    public Stream<Event> execute(ChangeFlightItineraryContext context) {
        if (!context.itineraryLive()) {
            throw new FlightItineraryNotFound("No flight itinerary found to change: " + itineraryId);
        }
        ItineraryChangePlan plan = plan(context);
        if (!plan.clean()) {
            throw new FlightItineraryRefused(plan.diffs().stream()
                    .filter(LegDiff::refused)
                    .map(diff -> new LegRefusal(diff.pasteNumber(), diff.refusal()))
                    .toList());
        }
        if (!plan.hasChanges()) {
            throw new FlightItineraryUnchanged(itineraryId);
        }
        Stream<Event> legEvents = plan.diffs().stream().flatMap(diff -> switch (diff.kind()) {
            case UNCHANGED -> Stream.<Event>empty();
            case CHANGED -> Stream.<Event>of(changed(diff.after()));
            case ADDED -> Stream.<Event>of(diff.after().booked());
            case REMOVED -> Stream.<Event>of(
                    new FlightCancelled(diff.before().flightId(), reason(), context.now(), dropCause()));
        });
        return Stream.concat(legEvents, Stream.of(itineraryEvent(membership(plan, context), context.now())));
    }

    private String reason() {
        return switch (kind) {
            case SCHEDULE_CHANGE -> SCHEDULE_CHANGE_REASON;
            case REBOOKING -> REBOOKING_REASON;
        };
    }

    private FlightCancellationCause dropCause() {
        return switch (kind) {
            case SCHEDULE_CHANGE -> FlightCancellationCause.AIRLINE_SCHEDULE_CHANGE;
            case REBOOKING -> FlightCancellationCause.REBOOKED;
        };
    }

    private Event itineraryEvent(List<FlightId> membership, Instant now) {
        return switch (kind) {
            case SCHEDULE_CHANGE -> new FlightItineraryChanged(itineraryId, membership, reason(), now);
            case REBOOKING -> new FlightItineraryRebooked(itineraryId, membership, reason(), now);
        };
    }

    private FlightChanged changed(ItineraryLeg leg) {
        return new FlightChanged(leg.flightId(), leg.airline(), leg.flightNumber(),
                leg.departureAirport(), leg.departureDateTime(),
                leg.arrivalAirport(), leg.arrivalDateTime(), reason());
    }

    /** Everyone who belongs to the trip afterwards, cancelled legs included, in departure order. */
    private static List<FlightId> membership(ItineraryChangePlan plan, ChangeFlightItineraryContext context) {
        List<ItineraryLeg> all = new ArrayList<>(plan.diffs().stream().map(LegDiff::effective).toList());
        all.addAll(context.cancelledMembers());
        all.addAll(context.droppedMembers());
        return all.stream()
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .map(ItineraryLeg::flightId)
                .toList();
    }
}
