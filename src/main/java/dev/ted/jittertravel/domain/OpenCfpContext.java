package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link OpenCfpCommand}, folded from the authoritative event stream (never from
 * a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param conferenceExists whether a live conference with this id exists: planned at some point and
 *                         neither cancelled by the organizers nor declined by Ted.
 * @param format           how the conference forms its program, so an {@code OPEN_SPACE} one can be
 *                         refused: it has no call for papers, hence no closing deadline. Added
 *                         2026-08-22 — the plan always specified this refusal and slice 3 shipped
 *                         without it. Null when {@code conferenceExists} is false, since a
 *                         conference that is not there has no format; the command refuses on
 *                         existence first, so it is never read in that case.
 *                         <p>
 *                         There is still no clock here, and that absence is the structural
 *                         guarantee that recording a CFP is not time-gated: a deadline that has
 *                         already passed is worth recording, because "this closed and I did not
 *                         submit" is a state the dashboard shows.
 */
public record OpenCfpContext(
        boolean conferenceExists,
        ConferenceFormat format
) implements DecisionContext {
}
