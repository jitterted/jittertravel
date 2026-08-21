package dev.ted.jittertravel.domain;

/**
 * Decision facts for {@link PlanGroundTransferCommand} — of which there are none.
 * <p>
 * Unlike a gathering or a private event, a ground transfer has <strong>no future-date rule</strong>
 * (D6 of {@code docs/archived/GroundTransferPlan.md}): Ted adds today's airport taxi to a trip already under
 * way, precisely to clear a problem that trip already raised. The only rule is that the arrival is
 * after the departure, and that is decided from the command's own fields.
 * <p>
 * So this record is deliberately empty. It exists only because {@code CommandExecutor.execute} takes
 * a {@link DecisionContext}. Do <em>not</em> add an unused {@code now} "for later" — that is a
 * speculative field, and adding one when a rule finally needs it is a one-line change.
 */
public record PlanGroundTransferContext() implements DecisionContext {
}
