package dev.ted.jittertravel.domain;

import java.time.ZoneId;

/**
 * Decision facts for {@link ChangeConferenceDatesCommand}, folded from the authoritative event
 * stream (never from a read model — R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param conferenceExists whether a live conference with this id exists: planned, and neither
 *                         cancelled by its organizers nor declined by Ted — the same liveness
 *                         {@link OpenCfpContext} uses. No clock: the change records what the
 *                         organizers did, and correcting a past conference's dates is legitimate.
 * @param zone             the venue zone the conference was planned in, {@code null} when it does
 *                         not exist. The new dates are stamped in it, so a change can never move the
 *                         conference — or a CFP deadline recorded in the same zone — to another one.
 */
public record ChangeConferenceDatesContext(
        boolean conferenceExists,
        ZoneId zone
) implements DecisionContext {
}
