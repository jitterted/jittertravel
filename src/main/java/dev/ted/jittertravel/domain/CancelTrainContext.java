package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link CancelTrainCommand}, folded from the authoritative event stream (never
 * from a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param trainExists whether a live train trip with this id exists: booked at some point and not
 *                    already cancelled. There is no other input — and in particular no clock,
 *                    because cancelling is not time-gated. A <em>past</em> trip is cancellable, and
 *                    is the entry most worth removing: it is the one still asserting Ted travelled
 *                    somewhere he did not. Compare {@link ChangeTrainContext}, which carries a
 *                    {@code now} precisely because changing <em>is</em> gated on the date.
 */
public record CancelTrainContext(
        boolean trainExists
) implements DecisionContext {
}
