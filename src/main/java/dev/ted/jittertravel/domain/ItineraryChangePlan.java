package dev.ted.jittertravel.domain;

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

/**
 * What a pasted change would do to an itinerary, leg by leg, and which legs break a rule.
 * The preview shows it and {@link ChangeFlightItineraryCommand#execute} writes it, from one
 * computation, so the page cannot promise something the command then refuses.
 * <p>
 * <strong>It does not depend on who made the change.</strong> A schedule change and a rebooking
 * meet the same rules and differ only in the fact recorded, which is why {@link #of} takes no
 * {@link ItineraryChangeKind}. The change page relies on that: it previews the diff before
 * "Who changed it?" is answered (the approved mockup's refused state shows both at once).
 * <p>
 * <strong>Matching</strong> happens within the itinerary only. Each pasted leg is paired with a live
 * booked leg first by flight number and departure day, then by route; the earliest unpaired booked
 * leg wins a tie. A pasted leg with no partner is added, a booked leg with no partner is dropped.
 * A pasted leg that only matches a leg Ted <em>cancelled</em> earlier is refused ({@link
 * LegCancelledEarlier}), whoever made the change (D12); one that matches a leg an earlier change
 * <em>dropped</em> is simply added as a new booking.
 * <p>
 * <strong>History is not revised</strong> (Ted, 2026-10-03): a booked leg that has already departed
 * and is in the paste must have the same times, or the change is refused. A departed leg the paste
 * leaves out is left alone, counted unchanged rather than dropped: an email sent mid-trip may list
 * only what is still to come (D6, 2026-10-13). Any other leg that moves or is added meets the rules
 * a single booking meets, except that the itinerary's own legs do not count as collisions — they
 * are the thing being rewritten — and the pasted legs must not overlap each other. Every refusal is
 * collected, so the page reports them together.
 */
public record ItineraryChangePlan(List<LegDiff> diffs) {

    public ItineraryChangePlan {
        diffs = List.copyOf(diffs);
    }

    /** The diff of a paste against the itinerary in {@code context}, in departure order, writing nothing. */
    public static ItineraryChangePlan of(List<ItineraryLeg> pastedLegs, ChangeFlightItineraryContext context) {
        return new Planner(List.copyOf(pastedLegs), context).plan();
    }

    public enum Kind { UNCHANGED, CHANGED, ADDED, REMOVED }

    /**
     * One row of the diff. {@code before} is the booked leg (null for an added one), {@code after}
     * the pasted leg carrying the booked flight's id where it matched (null for a removed one).
     * {@code pasteNumber} is the 1-based position in the paste, 0 for a leg the paste does not
     * mention: a removed one, or a flown one left alone (D6). {@code refusal} is null while the row
     * breaks no rule.
     */
    public record LegDiff(Kind kind, int pasteNumber, ItineraryLeg before, ItineraryLeg after,
                          RuntimeException refusal) {

        public boolean refused() {
            return refusal != null;
        }

        /** The leg as it will stand: the pasted one, or for a removal the one being dropped. */
        public ItineraryLeg effective() {
            return after != null ? after : before;
        }

        LegDiff refusedWith(RuntimeException reason) {
            return new LegDiff(kind, pasteNumber, before, after, reason);
        }
    }

    public boolean clean() {
        return diffs.stream().noneMatch(LegDiff::refused);
    }

    /** Whether any leg moves, arrives or goes. A paste identical to what is booked has none. */
    public boolean hasChanges() {
        return diffs.stream().anyMatch(diff -> diff.kind() != Kind.UNCHANGED);
    }

    /** How many rows are of this kind, for the preview's "1 leg added, 2 dropped" line. */
    public int count(Kind kind) {
        return (int) diffs.stream().filter(diff -> diff.kind() == kind).count();
    }

    /** One computation of a plan, holding what it needs across its steps. */
    private static final class Planner {
        private final List<ItineraryLeg> pastedLegs;
        private final ChangeFlightItineraryContext context;

        private Planner(List<ItineraryLeg> pastedLegs, ChangeFlightItineraryContext context) {
            this.pastedLegs = pastedLegs;
            this.context = context;
        }

        private ItineraryChangePlan plan() {
            Map<Integer, ItineraryLeg> liveMatch = new LinkedHashMap<>();
            Set<FlightId> claimed = new HashSet<>();
            List<Integer> pasteIndexes = IntStream.range(0, pastedLegs.size()).boxed().toList();
            match(pasteIndexes, context.liveMembers(), claimed, liveMatch);

            Map<Integer, ItineraryLeg> cancelledMatch = new LinkedHashMap<>();
            match(pasteIndexes, context.cancelledMembers(), new HashSet<>(), cancelledMatch);

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
                    .forEach(member -> diffs.add(member.departureDateTime().hasPassed(context.now())
                            ? new LegDiff(Kind.UNCHANGED, 0, member, member, null)
                            : new LegDiff(Kind.REMOVED, 0, member, null, null)));

            List<LegDiff> judged = diffs.stream().map(diff -> judge(diff, diffs, others)).toList();
            return new ItineraryChangePlan(judged.stream()
                    .sorted(Comparator.comparing(diff -> diff.effective().departureDateTime().utc()))
                    .toList());
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

        private Optional<ItineraryLeg> pairWith(List<ItineraryLeg> pool, Set<FlightId> claimed,
                                                Predicate<ItineraryLeg> fits) {
            Optional<ItineraryLeg> found = pool.stream()
                    .filter(candidate -> !claimed.contains(candidate.flightId()))
                    .filter(fits)
                    .findFirst();
            found.ifPresent(leg -> claimed.add(leg.flightId()));
            return found;
        }

        private LegDiff judge(LegDiff diff, List<LegDiff> all, ScheduledLegs others) {
            if (diff.refused() || diff.kind() == Kind.UNCHANGED) {
                return diff;
            }
            if (diff.before() != null && diff.before().departureDateTime().hasPassed(context.now())) {
                // Only a CHANGED leg reaches here: a departed leg the paste leaves out is UNCHANGED (D6).
                return diff.refusedWith(new FlownLegContradicted());
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

        private ItineraryLeg withId(ItineraryLeg leg, FlightId flightId) {
            return new ItineraryLeg(flightId, leg.airline(), leg.flightNumber(),
                    leg.departureAirport(), leg.departureDateTime(),
                    leg.arrivalAirport(), leg.arrivalDateTime());
        }

        /** The same journey: what an airline would call "no change", whatever zone the instant is shown in. */
        private boolean sameJourney(ItineraryLeg booked, ItineraryLeg pasted) {
            return booked.airline().equals(pasted.airline())
                   && booked.flightNumber().equals(pasted.flightNumber())
                   && booked.departureAirport().equals(pasted.departureAirport())
                   && booked.arrivalAirport().equals(pasted.arrivalAirport())
                   && booked.departureDateTime().utc().equals(pasted.departureDateTime().utc())
                   && booked.arrivalDateTime().utc().equals(pasted.arrivalDateTime().utc());
        }
    }
}
