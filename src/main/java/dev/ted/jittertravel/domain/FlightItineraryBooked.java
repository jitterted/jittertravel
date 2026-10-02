package dev.ted.jittertravel.domain;

import java.util.List;

/**
 * The legs of one airline confirmation were booked together, as one trip.
 * <p>
 * It exists to <em>group</em> legs, so that a later change or cancellation can act on the trip as a
 * unit (see {@code docs/FlightItineraryPlan.md}). Each leg is still its own {@link FlightBooked},
 * written in the same append; this event only names which flights belong together, so no read model
 * that shows or places a flight has to know it exists.
 * <p>
 * {@code confirmationCode} is a booking reference, and booking references are private (CLAUDE.md,
 * redaction): {@code PublicCalendarProjector} never reads this event, and nothing may publish it.
 */
public record FlightItineraryBooked(
        FlightItineraryId itineraryId,
        String airline,
        String confirmationCode,
        List<FlightId> flightIds
) implements Event {
    public FlightItineraryBooked {
        if (airline == null) {
            airline = "";
        }
        if (confirmationCode == null) {
            confirmationCode = "";
        }
        flightIds = flightIds == null ? List.of() : List.copyOf(flightIds);
    }
}
