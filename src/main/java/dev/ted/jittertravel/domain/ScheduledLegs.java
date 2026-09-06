package dev.ted.jittertravel.domain;

import java.util.List;
import java.util.Optional;

/**
 * Every scheduled leg currently on the books, as a decision fact.
 * <p>
 * <strong>A decision context carrying a collection is the point, not a cost</strong> (Ted,
 * 2026-09-06) — <em>"a pattern that will become more common as UI tasks become more complex"</em>.
 * A rule about what else is on the schedule can only be answered honestly by a context that carries
 * what else is on the schedule; the only alternative is a command reaching for a read model, which
 * R1 forbids for exactly this reason. So this is folded from the event stream, holds only
 * <em>live</em> legs (cancellations applied), and is shared by all four write paths rather than
 * folded four nearly-identical times.
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
     * <strong>{@code self} is excluded, and that is load-bearing.</strong> A change re-states the
     * leg being changed, so without this every edit would collide with itself and no booked journey
     * could ever be corrected again. {@code null} for a booking, which is not yet anything.
     * <p>
     * First rather than all: the form reports one blocking leg because that is the one to deal with,
     * and a second would be met on the next submit if it is still there.
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
