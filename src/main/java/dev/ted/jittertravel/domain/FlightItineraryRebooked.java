package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.List;

/**
 * Ted rebooked an itinerary himself, under the same confirmation code: some legs moved, some were
 * added, some dropped. The same shape as {@link FlightItineraryChanged}, and a different fact, so a
 * different event. Event names say what happened (Ted, 2026-10-12;
 * {@code docs/FlightItineraryRebookingPlan.md} D1).
 * <p>
 * Written in the same append as the {@link FlightChanged}, {@link FlightBooked} and
 * {@link FlightCancelled} that carry the legs' own changes. A leg it drops is cancelled with
 * {@link FlightCancellationCause#REBOOKED}.
 * <p>
 * <strong>A full snapshot of membership</strong>: every flight that belongs to the trip afterwards,
 * in departure order, including the legs it dropped. {@code rebookedOn} is when Ted recorded it,
 * captured at the boundary and a payload field (R11). {@code reason} is for recall only.
 */
public record FlightItineraryRebooked(
        FlightItineraryId itineraryId,
        List<FlightId> flightIds,
        String reason,
        Instant rebookedOn
) implements Event {
    public FlightItineraryRebooked {
        flightIds = flightIds == null ? List.of() : List.copyOf(flightIds);
        if (reason == null) {
            reason = "";
        }
    }
}
