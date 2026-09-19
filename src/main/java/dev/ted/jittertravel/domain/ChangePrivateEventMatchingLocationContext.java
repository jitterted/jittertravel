package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link ChangePrivateEventMatchingLocationCommand}, folded from the
 * authoritative event stream (never from a read model — R1 in
 * {@code EventSourcingRulesHeuristics.md}).
 *
 * @param privateEventExists whether a live private event with this id exists: planned at some point
 *                           and not already cancelled. No clock, for the same reason
 *                           {@link CancelPrivateEventContext} has none — and because a <em>past</em>
 *                           evening is the one still shaping away days with the wrong city, so
 *                           correcting it late is the point rather than an edge case.
 */
public record ChangePrivateEventMatchingLocationContext(
        boolean privateEventExists
) implements DecisionContext {
}
