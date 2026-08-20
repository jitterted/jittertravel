package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link ConfirmConferenceAttendanceCommand}, folded from the authoritative
 * event stream (never from a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param conferenceExists whether a live conference with this id exists: planned at some point and
 *                         neither cancelled by the organizers nor declined by Ted. The command has
 *                         no other input — confirming is not time-gated, so no clock is needed here.
 */
public record ConfirmConferenceAttendanceContext(
        boolean conferenceExists
) implements DecisionContext {
}
