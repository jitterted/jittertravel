package dev.ted.jittertravel.domain;

import java.util.List;
import java.util.Optional;

/**
 * Every scheduled leg currently on the books, as a decision fact.
 * <p>
 * A decision context carrying a collection is the point, not a cost (Ted, 2026-09-06). A rule about
 * what else is on the schedule can only be answered by a context carrying it; the alternative is a
 * command reading a projection, which R1 forbids. Folded from the event stream, holds only
 * <em>live</em> legs (cancellations applied), and shared by all four write paths.
 */
public record ScheduledLegs(List<ScheduledLeg> legs) {

    public ScheduledLegs {
        legs = List.copyOf(legs);
    }

    public static ScheduledLegs none() {
        return new ScheduledLegs(List.of());
    }

    /**
     * The first leg the proposed window collides with, if any.
     * <p>
     * {@code self} is excluded, and that is load-bearing: a change re-states the leg being changed,
     * so without it every edit would collide with itself and no booked journey could be corrected.
     * {@code null} for a booking, which is not yet anything.
     * <p>
     * First rather than all: the form reports the one leg to deal with, and a second is met on the
     * next submit if it is still there.
     */
    public Optional<ScheduledLeg> overlapping(ScheduledLegId self,
                                              ZonedTimestamp departure,
                                              ZonedTimestamp arrival) {
        return legs.stream()
                .filter(leg -> !leg.id().equals(self))
                .filter(leg -> leg.overlaps(departure, arrival))
                .findFirst();
    }
}
