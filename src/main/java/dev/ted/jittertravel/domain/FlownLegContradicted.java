package dev.ted.jittertravel.domain;

/**
 * A schedule change would rewrite a leg that has already departed: the paste gives it different
 * times, or leaves it out. What happened is not an airline's to revise, so the whole change is
 * refused (Ted, 2026-10-03).
 */
public class FlownLegContradicted extends RuntimeException {

    public enum How {
        TIMES_DIFFER("Already departed; the paste gives different times"),
        MISSING_FROM_PASTE("Already departed; the paste leaves it out");

        private final String message;

        How(String message) {
            this.message = message;
        }
    }

    private final How how;

    public FlownLegContradicted(How how) {
        super(how.message);
        this.how = how;
    }

    public How how() {
        return how;
    }
}
