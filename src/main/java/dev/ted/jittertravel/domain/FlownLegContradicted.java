package dev.ted.jittertravel.domain;

/**
 * A change would rewrite a leg that has already departed: the paste gives it different times.
 * What happened is not anyone's to revise, so the whole change is refused (Ted, 2026-10-03).
 * <p>
 * A departed leg the paste leaves out is <em>not</em> this: it is left alone, because an email
 * sent mid-trip may list only what is still to come ({@code docs/FlightItineraryRebookingPlan.md}
 * D6). Until 2026-11-14 that was refused too, as a second {@code How}.
 */
public class FlownLegContradicted extends RuntimeException {

    public FlownLegContradicted() {
        super("Already departed; the paste gives different times");
    }
}
