package dev.ted.jittertravel.domain;

import java.util.List;

/**
 * An itinerary broke one or more rules, and nothing was written. Carries <em>every</em> refusal,
 * each against the leg it belongs to — the rejected-form rule (CLAUDE.md): a trip has several legs
 * and one submit, so reporting only the first would turn each fix into a fresh surprise.
 */
public class FlightItineraryRefused extends RuntimeException {

    /**
     * One leg's refusal. {@code legNumber} is 1-based, as the email numbers them. {@code reason} is
     * the same exception the single-flight command would throw, so the boundary words it the same
     * way on both forms.
     */
    public record LegRefusal(int legNumber, RuntimeException reason) {
    }

    private final transient List<LegRefusal> refusals;

    public FlightItineraryRefused(List<LegRefusal> refusals) {
        super(refusals.size() + " leg(s) refused");
        this.refusals = List.copyOf(refusals);
    }

    public List<LegRefusal> refusals() {
        return refusals;
    }
}
