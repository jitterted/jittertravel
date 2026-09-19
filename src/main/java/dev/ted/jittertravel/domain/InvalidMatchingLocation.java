package dev.ted.jittertravel.domain;

/**
 * A matching location that cannot be a place. Today that means only "nothing was typed" — see
 * {@code docs/PrivateEventMatchingLocationPlan.md} O1 for why the station-shaped text
 * {@link EnteredLocation} refuses is deliberately not checked here yet.
 * <p>
 * The message is what the form renders under the input, so it is terse: the label above it has
 * already said which field this is (CLAUDE.md, "A rejected form reports everything it can see").
 */
public class InvalidMatchingLocation extends RuntimeException {
    public InvalidMatchingLocation(String message) {
        super(message);
    }
}
