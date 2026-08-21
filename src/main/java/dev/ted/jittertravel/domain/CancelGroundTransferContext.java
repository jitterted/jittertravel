package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link CancelGroundTransferCommand}, folded from the authoritative event
 * stream (never from a read model — see R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * @param transferExists whether a live transfer with this id exists: planned at some point and not
 *                       already cancelled. There is no other input — a transfer has no booking, no
 *                       deadline and no time gate, so no clock is needed. Compare
 *                       {@link PlanGroundTransferContext}, which is empty for the same reason.
 */
public record CancelGroundTransferContext(
        boolean transferExists
) implements DecisionContext {
}
