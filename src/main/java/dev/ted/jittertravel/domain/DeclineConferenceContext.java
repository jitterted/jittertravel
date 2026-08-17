package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link DeclineConferenceCommand}, folded from the authoritative event stream
 * (never from a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param conferenceExists whether a live conference with this id exists: tentatively planned at some
 *                         point and not already cancelled by the organizers or already declined. The
 *                         command has no other input — declining is not time-gated, so no clock is
 *                         needed here.
 */
public record DeclineConferenceContext(
        boolean conferenceExists
) implements DecisionContext {
}
