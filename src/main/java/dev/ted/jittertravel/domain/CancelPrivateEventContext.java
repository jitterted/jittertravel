package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link CancelPrivateEventCommand}, folded from the authoritative event stream
 * (never from a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param privateEventExists whether a live private event with this id exists: planned at some point
 *                           and not already cancelled. There is no other input — and in particular
 *                           no clock, because cancelling is not time-gated. A <em>past</em> private
 *                           event is cancellable, and is the entry most worth removing: it is the
 *                           one still asserting Ted was somewhere he was not. Compare
 *                           {@link PlanPrivateEventContext}, which carries a {@code now} precisely
 *                           because planning <em>is</em> gated on the date.
 */
public record CancelPrivateEventContext(
        boolean privateEventExists
) implements DecisionContext {
}
