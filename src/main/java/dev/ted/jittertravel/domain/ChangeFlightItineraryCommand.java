package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.domain.FlightItineraryRefused.LegRefusal;
import dev.ted.jittertravel.domain.ItineraryChangePlan.Kind;
import dev.ted.jittertravel.domain.ItineraryChangePlan.LegDiff;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Applies an airline's schedule change to an itinerary: the pasted legs are what the airline now
 * says, and the booked legs are made to agree. One command, several events, in one append — a
 * half-applied reroute (the new leg booked, the old one not cancelled) is exactly the overlapping
 * state the entry-time check exists to refuse.
 * <p>
 * <strong>Matching</strong> happens within the itinerary only. Each pasted leg is paired with a live
 * booked leg first by flight number and departure day, then by route; the earliest unpaired booked
 * leg wins a tie. A pasted leg with no partner is added, a booked leg with no partner is cancelled.
 * A pasted leg that only matches a leg <em>cancelled</em> earlier is refused ({@link
 * LegCancelledEarlier}).
 * <p>
 * <strong>History is not revised</strong> (Ted, 2026-10-03): a booked leg that has already departed
 * must be in the paste with the same times, or the change is refused. Any other leg that moves or is
 * added meets the rules a single booking meets, except that the itinerary's own legs do not count
 * as collisions — they are the thing being rewritten — and the pasted legs must not overlap each
 * other. Every refusal is collected and reported together, as in {@link BookFlightItineraryCommand}.
 * <p>
 * A paste that matches what is booked leg for leg is refused with {@link FlightItineraryUnchanged}
 * rather than recorded.
 */
public record ChangeFlightItineraryCommand(
        FlightItineraryId itineraryId,
        List<ItineraryLeg> pastedLegs
) implements DomainCommand<ChangeFlightItineraryContext> {

    static final String REASON = "Airline schedule change";

    public ChangeFlightItineraryCommand {
        if (pastedLegs == null || pastedLegs.isEmpty()) {
            throw new IllegalArgumentException("A schedule change has at least one leg");
        }
        pastedLegs = List.copyOf(pastedLegs);
    }

    /** The diff and every refusal against it, writing nothing; what the preview shows. */
    public ItineraryChangePlan plan(ChangeFlightItineraryContext context) {
        Map<Integer, ItineraryLeg> liveMatch = new LinkedHashMap<>();
        Set<FlightId> claimed = new HashSet<>();
        List<Integer> unmatched = indexes();
        match(unmatched, context.liveMembers(), claimed, liveMatch);

        List<Integer> leftover = unmatched.stream().filter(i -> !liveMatch.containsKey(i)).toList();
        Map<Integer, ItineraryLeg> cancelledMatch = new LinkedHashMap<>();
        match(leftover, context.cancelledMembers(), new HashSet<>(), cancelledMatch);

        ScheduledLegs others = new ScheduledLegs(context.scheduledLegs().legs().stream()
                .filter(leg -> context.liveMembers().stream().noneMatch(member ->
                        leg.id().equals(new ScheduledLegId.Flight(member.flightId()))))
                .toList());

        List<LegDiff> diffs = new ArrayList<>();
        for (int i = 0; i < pastedLegs.size(); i++) {
            ItineraryLeg pasted = pastedLegs.get(i);
            ItineraryLeg booked = liveMatch.get(i);
            if (booked != null) {
                ItineraryLeg after = withId(pasted, booked.flightId());
                diffs.add(new LegDiff(sameJourney(booked, after) ? Kind.UNCHANGED : Kind.CHANGED,
                        i + 1, booked, after, null));
            } else {
                RuntimeException refusal = cancelledMatch.containsKey(i) ? new LegCancelledEarlier() : null;
                diffs.add(new LegDiff(Kind.ADDED, i + 1, null, pasted, refusal));
            }
        }
        context.liveMembers().stream()
                .filter(member -> !claimed.contains(member.flightId()))
                .forEach(member -> diffs.add(new LegDiff(Kind.REMOVED, 0, member, null, null)));

        List<LegDiff> judged = diffs.stream().map(diff -> judge(diff, diffs, others, context)).toList();
        return new ItineraryChangePlan(judged.stream()
                .sorted(Comparator.comparing(diff -> diff.effective().departureDateTime().utc()))
                .toList());
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
                    new FlightCancelled(diff.before().flightId(), REASON, context.now()));
        });
        return Stream.concat(legEvents,
                Stream.of(new FlightItineraryChanged(itineraryId, membership(plan, context), REASON, context.now())));
    }

    private static FlightChanged changed(ItineraryLeg leg) {
        return new FlightChanged(leg.flightId(), leg.airline(), leg.flightNumber(),
                leg.departureAirport(), leg.departureDateTime(),
                leg.arrivalAirport(), leg.arrivalDateTime(), REASON);
    }

    /** Everyone who belongs to the trip afterwards, cancelled legs included, in departure order. */
    private static List<FlightId> membership(ItineraryChangePlan plan, ChangeFlightItineraryContext context) {
        List<ItineraryLeg> all = new ArrayList<>(plan.diffs().stream().map(LegDiff::effective).toList());
        all.addAll(context.cancelledMembers());
        return all.stream()
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .map(ItineraryLeg::flightId)
                .toList();
    }

    private List<Integer> indexes() {
        return IntStream.range(0, pastedLegs.size()).boxed().toList();
    }

    /** Pairs pasted legs with the pool: same flight number and day first, then same route. */
    private void match(List<Integer> pasted, List<ItineraryLeg> pool, Set<FlightId> claimed,
                       Map<Integer, ItineraryLeg> matches) {
        List<ItineraryLeg> byDeparture = pool.stream()
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .toList();
        for (int i : pasted) {
            ItineraryLeg leg = pastedLegs.get(i);
            pairWith(byDeparture, claimed, candidate ->
                    candidate.flightNumber().equals(leg.flightNumber())
                    && candidate.departureDateTime().localDateTime().toLocalDate()
                            .equals(leg.departureDateTime().localDateTime().toLocalDate()))
                    .ifPresent(booked -> matches.put(i, booked));
        }
        for (int i : pasted) {
            if (matches.containsKey(i)) {
                continue;
            }
            ItineraryLeg leg = pastedLegs.get(i);
            pairWith(byDeparture, claimed, candidate ->
                    candidate.departureAirport().equals(leg.departureAirport())
                    && candidate.arrivalAirport().equals(leg.arrivalAirport()))
                    .ifPresent(booked -> matches.put(i, booked));
        }
    }

    private static Optional<ItineraryLeg> pairWith(List<ItineraryLeg> pool, Set<FlightId> claimed,
                                                   Predicate<ItineraryLeg> fits) {
        Optional<ItineraryLeg> found = pool.stream()
                .filter(candidate -> !claimed.contains(candidate.flightId()))
                .filter(fits)
                .findFirst();
        found.ifPresent(leg -> claimed.add(leg.flightId()));
        return found;
    }

    private LegDiff judge(LegDiff diff, List<LegDiff> all, ScheduledLegs others,
                          ChangeFlightItineraryContext context) {
        if (diff.refused() || diff.kind() == Kind.UNCHANGED) {
            return diff;
        }
        if (diff.before() != null && diff.before().departureDateTime().hasPassed(context.now())) {
            return diff.refusedWith(new FlownLegContradicted(diff.kind() == Kind.REMOVED
                    ? FlownLegContradicted.How.MISSING_FROM_PASTE
                    : FlownLegContradicted.How.TIMES_DIFFER));
        }
        if (diff.kind() == Kind.REMOVED) {
            return diff;
        }
        ItineraryLeg leg = diff.after();
        if (!leg.departureDateTime().utc().isAfter(context.now())) {
            return diff.refusedWith(new DepartureNotInFuture("Departure date/time must be in the future"));
        }
        if (!leg.arrivalDateTime().utc().isAfter(leg.departureDateTime().utc())) {
            return diff.refusedWith(new InvalidDateRange("Arrival date/time must be after departure date/time"));
        }
        Optional<ScheduledLeg> booked = others.overlapping(null, leg.departureDateTime(), leg.arrivalDateTime());
        if (booked.isPresent()) {
            return diff.refusedWith(new OverlappingLegRefused(booked.get()));
        }
        return all.stream()
                .filter(other -> other != diff && other.kind() != Kind.REMOVED)
                .filter(other -> new ScheduledLeg(new ScheduledLegId.Flight(other.after().flightId()),
                        other.after().departureDateTime(), other.after().arrivalDateTime())
                        .overlaps(leg.departureDateTime(), leg.arrivalDateTime()))
                .findFirst()
                .map(other -> diff.refusedWith(new OverlapsAnotherItineraryLeg(other.pasteNumber())))
                .orElse(diff);
    }

    private static ItineraryLeg withId(ItineraryLeg leg, FlightId flightId) {
        return new ItineraryLeg(flightId, leg.airline(), leg.flightNumber(),
                leg.departureAirport(), leg.departureDateTime(),
                leg.arrivalAirport(), leg.arrivalDateTime());
    }

    /** The same journey: what an airline would call "no change", whatever zone the instant is shown in. */
    private static boolean sameJourney(ItineraryLeg booked, ItineraryLeg pasted) {
        return booked.airline().equals(pasted.airline())
               && booked.flightNumber().equals(pasted.flightNumber())
               && booked.departureAirport().equals(pasted.departureAirport())
               && booked.arrivalAirport().equals(pasted.arrivalAirport())
               && booked.departureDateTime().utc().equals(pasted.departureDateTime().utc())
               && booked.arrivalDateTime().utc().equals(pasted.arrivalDateTime().utc());
    }
}
