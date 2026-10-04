package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.domain.FlightItineraryRefused.LegRefusal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Books every leg of one airline confirmation, and records that they belong together.
 * <p>
 * <strong>One command, several events, and that is the atomicity.</strong> {@code CommandExecutor}
 * appends everything a command emits in one transaction, so the legs land together or not at all.
 * An {@code ItineraryToLegProcessor} issuing one command per leg was considered and rejected: a leg
 * refused after the page said "done" is the same partial booking, only later, and needs a whole
 * failure path to surface it (see {@code docs/archived/FlightItineraryPlan.md}, section 8).
 * <p>
 * Each leg meets the rules a single booking meets ({@link BookFlightCommand}), in the same order:
 * departure in the future, arrival after departure, no collision with a leg already booked. Plus one
 * a single booking cannot need — <strong>no two legs of this itinerary may overlap</strong>, which
 * {@link ScheduledLegs} cannot see because neither leg is booked yet. Every refusal is collected
 * and thrown together; a leg whose own window is invalid is not also checked for overlaps, because
 * an overlap is only a meaningful question once the window is.
 */
public record BookFlightItineraryCommand(
        FlightItineraryId itineraryId,
        String airline,
        String confirmationCode,
        List<ItineraryLeg> legs
) implements DomainCommand<BookFlightContext> {

    public BookFlightItineraryCommand {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("An itinerary has at least one leg");
        }
        legs = List.copyOf(legs);
        if (airline == null) {
            airline = "";
        }
        if (confirmationCode == null) {
            confirmationCode = "";
        }
    }

    @Override
    public Stream<Event> execute(BookFlightContext context) {
        List<LegRefusal> refusals = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) {
            refusalFor(i, context).ifPresent(refusals::add);
        }
        if (!refusals.isEmpty()) {
            throw new FlightItineraryRefused(refusals);
        }
        return Stream.concat(
                legs.stream().map(ItineraryLeg::booked),
                Stream.of(new FlightItineraryBooked(itineraryId, airline, confirmationCode,
                        legs.stream().map(ItineraryLeg::flightId).toList())));
    }

    private Optional<LegRefusal> refusalFor(int index, BookFlightContext context) {
        ItineraryLeg leg = legs.get(index);
        int number = index + 1;
        if (!leg.departureDateTime().utc().isAfter(context.now())) {
            return refused(number, new DepartureNotInFuture("Departure date/time must be in the future"));
        }
        if (!leg.arrivalDateTime().utc().isAfter(leg.departureDateTime().utc())) {
            return refused(number, new InvalidDateRange("Arrival date/time must be after departure date/time"));
        }
        var booked = context.scheduledLegs()
                .overlapping(null, leg.departureDateTime(), leg.arrivalDateTime());
        if (booked.isPresent()) {
            return refused(number, new OverlappingLegRefused(booked.get()));
        }
        for (int other = 0; other < legs.size(); other++) {
            if (other != index && overlaps(leg, legs.get(other))) {
                return refused(number, new OverlapsAnotherItineraryLeg(other + 1));
            }
        }
        return Optional.empty();
    }

    private static boolean overlaps(ItineraryLeg leg, ItineraryLeg other) {
        // The half-open predicate every overlap in the tree uses: a connection is not a conflict.
        return new ScheduledLeg(new ScheduledLegId.Flight(other.flightId()),
                                other.departureDateTime(), other.arrivalDateTime())
                .overlaps(leg.departureDateTime(), leg.arrivalDateTime());
    }

    private static Optional<LegRefusal> refused(int number, RuntimeException reason) {
        return Optional.of(new LegRefusal(number, reason));
    }
}
