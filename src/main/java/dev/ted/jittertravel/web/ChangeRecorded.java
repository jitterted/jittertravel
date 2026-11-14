package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryChangeOutcomesProjector.Outcome;

/**
 * The banner on {@code /booked-flights} after a change to an itinerary is recorded, as the approved
 * mockup's after-apply screen draws it: "<strong>Rebooking of HE80PP recorded:</strong> 1 leg
 * dropped." ({@code docs/FlightItineraryRebookingPlan.md}, approved item 2). The list showed no
 * message before this, so the result travels back to it as an itinerary id and an outcome key, and
 * the words are built here from the read model, never from the URL.
 */
public record ChangeRecorded(String confirmationCode, Outcome outcome) {

    public String headline() {
        return switch (outcome.kind()) {
            case REBOOKING -> "Rebooking of " + confirmationCode + " recorded:";
            case SCHEDULE_CHANGE -> "Schedule change to " + confirmationCode + " recorded:";
        };
    }

    /** "1 leg added, 2 dropped." The legs a change leaves alone are not news here. */
    public String counts() {
        return ItineraryChangePreview.counted(outcome.added(), outcome.moved(), outcome.dropped(), 0) + ".";
    }
}
