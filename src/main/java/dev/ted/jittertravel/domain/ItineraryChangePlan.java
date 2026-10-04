package dev.ted.jittertravel.domain;

import java.util.List;

/**
 * What a pasted schedule change would do to an itinerary, leg by leg, and which legs break a rule.
 * The preview shows it and {@link ChangeFlightItineraryCommand#execute} writes it, from one
 * computation, so the page cannot promise something the command then refuses.
 */
public record ItineraryChangePlan(List<LegDiff> diffs) {

    public ItineraryChangePlan {
        diffs = List.copyOf(diffs);
    }

    public enum Kind { UNCHANGED, CHANGED, ADDED, REMOVED }

    /**
     * One row of the diff. {@code before} is the booked leg (null for an added one), {@code after}
     * the pasted leg carrying the booked flight's id where it matched (null for a removed one).
     * {@code pasteNumber} is the 1-based position in the paste, 0 for a removed leg, which the paste
     * does not mention. {@code refusal} is null while the row breaks no rule.
     */
    public record LegDiff(Kind kind, int pasteNumber, ItineraryLeg before, ItineraryLeg after,
                          RuntimeException refusal) {

        public boolean refused() {
            return refusal != null;
        }

        /** The leg as it will stand: the pasted one, or for a removal the one being dropped. */
        public ItineraryLeg effective() {
            return after != null ? after : before;
        }

        LegDiff refusedWith(RuntimeException reason) {
            return new LegDiff(kind, pasteNumber, before, after, reason);
        }
    }

    public boolean clean() {
        return diffs.stream().noneMatch(LegDiff::refused);
    }

    /** Whether any leg moves, arrives or goes. A paste identical to what is booked has none. */
    public boolean hasChanges() {
        return diffs.stream().anyMatch(diff -> diff.kind() != Kind.UNCHANGED);
    }
}
